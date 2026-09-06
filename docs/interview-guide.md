# Interview guide

Questions an interviewer is likely to ask about this project, with answers grounded in
what the code actually does — including the parts that are not finished.

A note on how to use this: the strongest answers here are the ones where you can name
the trade-off and the thing you *didn't* do. Interviewers probe for whether you
understand your own design or recite it.

---

## Architecture

### 1. Walk me through what happens when a customer places an order.

The request hits the gateway, which verifies the JWT, stamps a correlation id and routes
to order-service. Order-service calls product-service over Feign for each line to get the
current name and price, and copies the price onto the order item — so a later catalogue
change can't rewrite what the customer agreed to pay. It persists the order, its items
and a status-history row in one local transaction.

Then it calls inventory-service synchronously to reserve stock. That's the one blocking
call that can fail the request, because the customer needs an immediate yes or no on
availability. If it succeeds, the order moves through `INVENTORY_RESERVED` to
`PAYMENT_PENDING`, and the customer gets a 201.

After the transaction commits, `OrderCreated` goes to Kafka. Payment-service consumes it,
charges, and publishes `PaymentCompleted` or `PaymentFailed`. Order-service consumes that
and confirms or fails the order; inventory-service consumes the same event and either
confirms the stock consumption or releases it; notification-service consumes everything
and records a notification.

### 2. Why microservices rather than a modular monolith?

Honestly — for a system at this scale, a well-structured monolith would be simpler,
faster and easier to operate. Microservices earn their cost when teams need to deploy
independently, or when components have genuinely different scaling profiles.

What this design does do is make the boundaries real rather than notional:
database-per-service means no one can quietly join across a boundary, which is how
"modular" monoliths usually decay. The concrete payoff visible in the code is
notification-service — it was added by subscribing to topics that already existed,
without touching order-service or payment-service at all.

### 3. How did you decide the service boundaries?

By business capability, not by technical layer. The test I applied: can this service be
changed and deployed without coordinating with another? Inventory owns stock and knows
nothing about prices; product owns prices and knows nothing about stock. They're separate
because they change for different reasons and at different rates.

The anti-pattern would be a "database service" or an "entity service" — that's layering,
not decomposition, and every feature then touches every service.

### 4. Why does order-service call inventory synchronously but payment asynchronously?

The rule is: synchronous when the caller can't proceed without the answer.

The customer can't be told their order is placed until we know the stock exists, so
reservation blocks. Payment can take seconds against a real provider, and the customer
shouldn't wait on that — so it's an event. It also means a payment outage degrades the
system (orders queue up) rather than stopping it (checkout fails).

Every synchronous hop is a coupling and a shared failure mode, so there are only two, both
on the path where someone is actually waiting.

### 5. What does the API Gateway do, and why not let clients call services directly?

Routing, JWT verification, rate limiting, correlation-id generation, and being the single
public surface. Without it, every service needs its own TLS termination, its own rate
limiter, and its own CORS config, and clients need to know the topology.

It's also a single point of failure and a potential bottleneck — which is why it's
stateless and runs at two replicas in the Kubernetes manifests.

---

## Distributed transactions and consistency

### 6. You have an order in one database and inventory in another. How do you keep them consistent?

I don't use a distributed transaction. Two-phase commit would hold locks across a network
call and couple the availability of both services — if either coordinator or participant
dies mid-protocol, rows stay locked.

Instead it's a **saga**: a sequence of local ACID transactions, each with a compensating
action. Order is created locally, stock is reserved locally, payment happens locally. If
payment fails, the compensation is releasing the stock — not rolling back a transaction
that was already committed.

The trade-off is that there's a window where the order says `PAYMENT_PENDING` and stock is
held but no money has moved. That's eventual consistency, and it's the price of
availability.

### 7. Orchestration or choreography?

Both, deliberately.

Steps up to reservation are **orchestrated** by order-service, because the customer is
waiting on one synchronous response and someone has to own that decision — you want one
readable place to look when an interactive flow breaks.

Everything after payment is **choreographed**: payment publishes an outcome and
order-service, inventory-service and notification-service each react independently. No
orchestrator has to know that notification-service exists.

Neither is universally better. Orchestration gives you a visible flow and a single point
of failure; choreography gives you decoupling and a flow you can only reconstruct from
logs.

### 8. What's the dual-write problem and how did you handle it?

Writing to your database and publishing to a broker are two systems with no shared
transaction. Do it naively and you get one of two bugs: publish inside the transaction and
a later rollback means you've announced an order that doesn't exist — payment then charges
for nothing. Or publish after the commit and a crash in between loses the event.

I handle the first case: events are published from
`@TransactionalEventListener(phase = AFTER_COMMIT)`. The service raises a Spring
application event inside the transaction and the Kafka send only fires if the commit
succeeds.

I do **not** fully handle the second. If the commit succeeds and the broker is unreachable
right after, the event is lost. The fix is a transactional outbox — write the event to an
`outbox` table in the same transaction and have a relay publish from it. That's in the
future-enhancements list rather than claimed as done.

### 9. What is a transactional outbox and why didn't you build it?

You insert the event into an `outbox` table in the same transaction as the business
write, so either both happen or neither does. A separate process (a poller, or CDC via
Debezium) reads that table and publishes to Kafka, marking rows as sent. It converts the
dual write into a single atomic write plus an at-least-once relay.

I didn't build it because it adds a table, a relay process and a new failure mode to
operate, and the `AFTER_COMMIT` approach closes the more damaging half of the problem.
It's the right next step, and I'd want it before this handled real money.

### 10. How long is the inconsistency window, and what if payment never responds?

Normally sub-second. The window is bounded because payment either succeeds or fails.

But if the event is lost or payment-service never processes it, the order sits in
`PAYMENT_PENDING` forever with stock held. That's a real gap in this implementation. The
fix is a scheduled sweeper — a job that finds orders past a deadline in that state and
compensates them. It's listed as a known limitation rather than glossed over.

---

## Kafka and idempotency

### 11. Kafka gives at-least-once delivery. What does that mean for your consumers?

That every consumer will see the same event twice sooner or later — after a rebalance, a
restart, or a commit that didn't land. So idempotency isn't a defensive extra, it's a
requirement of the design. Without it, a redelivered `PaymentCompleted` confirms an order
twice, and a redelivered `OrderCreated` charges a customer twice.

### 12. Show me three different ways you made consumers idempotent.

**A unique constraint** — payment-service. `payments.order_id` is `UNIQUE`. This matters
because two concurrent deliveries can *both* pass an application-level "does a payment
exist?" check — they both read before either writes. Only one can insert. The database is
the guarantee; the code-level check is just a fast path.

**An explicit ledger** — order-service. A `processed_events` table keyed by `event_id`.
Used here because the work (a status transition plus publishing another event) has no
natural unique key of its own.

**Existing domain state** — inventory-service. The audit trail already answers "is there a
RESERVE transaction for this order?", so no extra table is needed.

The general point: idempotency is cheapest when something you already store encodes "this
was done."

### 13. Why is the order id the Kafka partition key?

Kafka only guarantees ordering within a partition. Keying by order id puts every event for
one order on the same partition, so `OrderCreated` is always processed before
`PaymentCompleted` for that order. Different orders land on different partitions and
process in parallel, so you keep throughput.

If I keyed randomly, a consumer could process a payment result for an order it hadn't seen
created yet.

### 14. What happens to a message your consumer can't process?

A `DefaultErrorHandler` retries three times with a one-second backoff. If it still fails,
a `DeadLetterPublishingRecoverer` sends it to `<topic>.DLT` on the same partition and
commits the offset.

That last part is the point. Without it, a single poison message blocks its partition
forever — every subsequent event for those orders stops. The DLT lets the partition keep
moving while preserving the bad message for inspection.

### 15. How do you guarantee a message is actually published?

Producers use `acks=all` with `enable.idempotence=true`, so the write is acknowledged by
all in-sync replicas and a producer retry can't create a duplicate.

But as I said on the dual write — that's the broker side. The gap between the database
commit and the send is not closed without an outbox.

---

## Concurrency and locking

### 16. Two customers order the last item at the same time. What happens?

Without protection: both read `quantity_available = 1`, both check `1 >= 1`, both write
`0`. One unit sold twice.

The reserve path uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`, which issues
`SELECT … FOR UPDATE`. The second transaction blocks until the first commits, then reads
`available = 0` and is correctly rejected with a 409.

### 17. Why pessimistic and not optimistic locking there?

Optimistic locking would detect the conflict — the second write fails a version check —
but it converts the problem into a retry the caller has to handle, on the hottest row in
the system. During a flash sale on one product you'd get a storm of version conflicts and
retries.

Pessimistic locking serialises access to that row, which is exactly what you want when
contention is expected and a conflict is expensive. Everywhere else — orders, products,
users — contention is low, so `@Version` is right and costs nothing when there's no
conflict.

### 18. Doesn't `SELECT FOR UPDATE` risk deadlocks?

Yes, and there's a specific one here: two orders each containing products A and B in
opposite order. T1 locks A and wants B; T2 locks B and wants A.

The reserve path sorts items by product id before locking, so every transaction acquires
locks in the same order. A cycle becomes impossible. It's three characters of `.sorted()`
and it eliminates a whole class of production incident.

### 19. Where is the lock held, and for how long?

For the duration of the reservation transaction — which contains only local database work.
Deliberately no remote calls happen while it's held. Holding a row lock across a network
call means holding it for the duration of somebody else's outage.

---

## Caching

### 20. What do you cache, and what don't you?

Cached: product lookups by id, 10-minute TTL. It's the highest-volume read and the data
changes rarely.

Not cached: stock levels and order status. Both change constantly and a stale read there
is a correctness bug, not a performance win — you'd tell a customer an item is in stock
when it isn't.

### 21. How do you handle invalidation?

`@CacheEvict` on update and delete, keyed by product id. With a TTL alone, a price change
would be invisible for up to ten minutes and customers would be quoted stale prices. The
eviction is the correctness mechanism; the TTL is a backstop against drift.

### 22. Why cache the DTO instead of the entity?

Caching a JPA entity serialises Hibernate proxies and lazy-loading state. Deserialise it
later and you have an object detached from any persistence context whose lazy fields throw
the moment you touch them. The DTO is a plain, closed object.

I also store JSON rather than Java serialization, so the cache is readable with
`redis-cli` and survives a field being added to the DTO.

### 23. What if Redis goes down?

Cache operations fail and the reads fall through to PostgreSQL — degraded latency, not an
outage. What I have *not* implemented is protection against a cache stampede: if the cache
is cold and a popular product gets a burst of traffic, every request goes to the database
simultaneously. A short-lived lock or a probabilistic early refresh would fix that.

---

## Resilience

### 24. Why a circuit breaker on the inventory call?

Inventory is on the critical path of checkout. If it's slow rather than down, order-service
threads pile up waiting on it until the pool is exhausted — and then endpoints with nothing
to do with inventory start failing too. That's how one service's problem becomes an outage.

The breaker opens above a 50% failure rate over a 10-call window and fails fast for 10
seconds before probing again. Retry (3 attempts, exponential backoff) handles a transient
blip; the breaker handles a real outage.

### 25. What does your fallback return?

An error — `503 SERVICE_UNAVAILABLE`. Deliberately not a fake success.

This is the question I'd want to be asked. A fallback that returned "reserved" when
inventory was unreachable would let the order proceed to payment for stock that was never
held. You'd charge a customer for an item you can't ship, and you'd find out days later at
fulfilment. When the truth is unknown, an error is the correct answer. Fallbacks should
degrade gracefully, not lie.

### 26. Which service failures stop the system, and which just degrade it?

Stops it: inventory (no orders can be accepted), product (can't price an order).
Degrades it: payment (orders are accepted, events queue in Kafka, processed on recovery),
notification (no customer-visible impact at all), Kafka (synchronous paths still work).

That asymmetry is a direct consequence of what I made synchronous. It's a design output,
not an accident.

---

## Security

### 27. Walk me through the authentication flow.

Login validates credentials with BCrypt and issues an HS256 JWT containing the user id as
subject, plus role and type claims. The client sends it as a bearer token. The gateway
verifies it and forwards the identity downstream; each service verifies it again and
populates the Spring Security context.

### 28. Why verify at the gateway *and* at every service?

Defence in depth. If a service is reachable directly inside the cluster — a
misconfigured NetworkPolicy, a compromised pod, a developer port-forwarding — gateway-only
verification means it's completely unprotected.

Trusting a gateway-set `X-User-Id` header alone would make that header a forgeable
credential: anyone who can reach the pod can set it to any user id.

### 29. Why BCrypt rather than SHA-256?

SHA-256 is designed to be fast, which is exactly wrong for passwords — it makes brute
force cheap. BCrypt is deliberately slow and has a tunable work factor you raise as
hardware improves, and it salts each hash automatically so identical passwords produce
different hashes and rainbow tables don't apply.

### 30. Where are your secrets?

Not in source. `jwt.secret` comes from the `JWT_SECRET` environment variable, and
`JwtService` throws at construction if it's missing or under 32 bytes — a misconfiguration
fails loudly at boot rather than quietly producing weak or forgeable tokens. `.env` is
gitignored; the value in `.env.example` is a documented development-only default.

In production this would come from Secrets Manager or Vault, injected at runtime.

### 31. How do you prevent account enumeration?

A wrong password and an unknown username return the identical error message and status. If
they differed, the login endpoint becomes an oracle for which accounts exist. There's a
unit test asserting exactly this, because it's the kind of thing a refactor quietly breaks.

### 32. Why is CSRF disabled?

Because the API is stateless and token-based. CSRF exploits ambient credentials — cookies
the browser attaches automatically. A bearer token in an `Authorization` header isn't sent
automatically, so there's nothing to forge. Disabling it is a decision that follows from
statelessness, not a shortcut.

---

## Data and JPA

### 33. Why is the price copied onto `order_items` instead of referenced?

Because an order is a historical record of an agreement. If I stored only `product_id` and
joined for the price, a catalogue update would retroactively change what every past
customer paid. Invoices wouldn't reconcile.

The same reasoning applies to `product_name` — the customer bought the thing that had that
name at that time.

### 34. `DECIMAL` or `DOUBLE` for money?

`DECIMAL`, always. Binary floating point can't represent 0.10 exactly. Sum enough order
lines in a `double` and you get cent-level drift that surfaces in reconciliation and is
miserable to trace. `BigDecimal` in Java, `DECIMAL(14,2)` in Postgres.

### 35. Why Liquibase instead of `ddl-auto: update`?

`ddl-auto: update` is not a migration tool — it can't drop a column, can't rename safely,
can't be reviewed, and can't be rolled back. It's also non-deterministic across Hibernate
versions.

Liquibase gives versioned, reviewable, ordered changesets with rollback blocks, and it's
the same path in every environment. Hibernate runs with `validate`, so a mapping that
drifts from the schema is a startup failure rather than a runtime surprise.

### 36. What's the N+1 problem and where does it appear here?

Loading an order and then lazily loading its items triggers one query for the order and
one per item. On a list endpoint that's one query plus N per row.

`OrderRepository.findWithItemsById` uses `@EntityGraph(attributePaths = "items")` to fetch
items in the same query. The list endpoint deliberately returns a summary projection
without items at all — the cheapest fix for N+1 is usually not fetching what you don't
need.

### 37. Why check constraints when you already validate in the application?

Because application validation can be bypassed — a bad migration, a manual `UPDATE`, a new
code path that forgets. `ck_inventory_available_non_negative` means stock cannot go
negative no matter what any future version of the code does. The database is the last line
of defence and the only one that can't be forgotten.

---

## Testing and operations

### 38. What did you test, and what didn't you?

I tested behaviour where a bug would be expensive: reservation and release idempotency,
insufficient-stock rejection, order pricing and totals, cancellation rules, payment
approval and decline, and the fact that a wrong password is indistinguishable from an
unknown user.

I didn't write tests for getters, mappers, or controller wiring. Coverage is an output, not
a target — tests written to raise a number are maintenance cost with no defect-detection
value.

### 39. Why Testcontainers rather than H2?

Because H2 isn't PostgreSQL. Different SQL dialect, different locking semantics, and
`SELECT FOR UPDATE` behaves differently — so the one thing I most need to test would be
tested against the wrong engine. Testcontainers runs the real PostgreSQL with the real
Liquibase migrations. It's slower, which is why unit tests (`*Test`) and integration tests
(`*IT`) are separate Maven phases.

### 40. How would you debug a customer saying "my order is stuck"?

Ask for the `traceId` from the response — every API response carries the correlation id.
Then grep it across all services; because the gateway stamps it and every service puts it
in the MDC, that one id returns every log line for that customer action across nine
services in causal order.

Then check `order_status_history` for where it stopped, and Kafka consumer lag to see
whether an event was never consumed.

### 41. What would break first under load?

The shared PostgreSQL instance — six logical databases on one server is a local
convenience and a production single point of failure. Then the synchronous inventory hop
during a flash sale on one product, because the row lock serialises every order for it.
Then Kafka consumer lag on notification-service.

I'd split the databases first, then consider batching or queueing reservations for
hot products.

### 42. What would you do differently if you started again?

Two things. I'd build the transactional outbox from the start rather than adding it later
— retrofitting event reliability is harder than designing for it. And I'd seriously
consider starting as a modular monolith with these same boundaries and extracting services
when a real reason appeared, because a lot of the operational cost here buys independence
that a single developer doesn't yet need.

---

## Questions worth asking back

- What does the consistency window actually cost this business? That decides whether the
  saga is right or whether you need something stricter.
- What's the read/write ratio on the catalogue? It changes whether caching or read
  replicas is the better first move.
- How is on-call structured? Choreography is harder to debug at 3am than orchestration,
  and that's a real operational cost, not just an architectural preference.

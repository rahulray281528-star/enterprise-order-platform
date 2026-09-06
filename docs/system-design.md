# System design

## Contents
- [Problem](#problem)
- [High-level design](#high-level-design)
- [Service responsibilities](#service-responsibilities)
- [Communication patterns](#communication-patterns)
- [The order saga](#the-order-saga)
- [Transaction boundaries](#transaction-boundaries)
- [Consistency and the dual-write problem](#consistency-and-the-dual-write-problem)
- [Idempotency](#idempotency)
- [Concurrency and locking](#concurrency-and-locking)
- [Caching](#caching)
- [Fault tolerance](#fault-tolerance)
- [Security](#security)
- [Observability](#observability)
- [Scalability](#scalability)
- [Known limitations](#known-limitations)

---

## Problem

Accept customer orders against a shared inventory, take payment, and keep order state,
stock levels and customer notifications consistent — without a distributed transaction,
and without overselling.

Functional requirements: register and authenticate; browse a catalogue; place an order;
reserve stock; take payment; confirm or compensate; notify.

Non-functional requirements that actually shaped the design:
- Never oversell. Two customers must not both buy the last unit.
- Never charge twice. A retried message must not become a second charge.
- A slow or dead payment provider must not take down checkout.
- Every failure must be traceable across services from a single id.

---

## High-level design

Nine services, database-per-service, one gateway as the only public entry point.

```
                          ┌───────────────┐
   client ───────────────►│  API Gateway  │  JWT, routing, rate limit, correlation id
                          └───────┬───────┘
                                  │
      ┌───────────┬───────────┬───┴───────┬───────────┬──────────────┐
      ▼           ▼           ▼           ▼           ▼              ▼
   auth       product     inventory     order      payment     notification
   auth_db   product_db  inventory_db  order_db   payment_db  notification_db
                  │           ▲           │           │              ▲
                  │  Feign    │           │  Feign    │              │
                  └───────────┴───────────┘           │              │
                                  │                   │              │
                                  └────► Kafka ◄──────┴──────────────┘
```

**Why database-per-service.** A shared database makes service boundaries advisory: any
team can join across them and the boundary erodes within a quarter. Separate databases
make the boundary physical. The cost is real — no cross-service joins, no foreign keys
across services, and eventual consistency between them — and the system is designed
around that cost rather than pretending it away.

**Why a gateway.** One place to terminate TLS, verify tokens, rate limit and stamp
correlation ids, instead of nine.

**Why Eureka.** Services scale horizontally and get new addresses; clients resolve by
logical name (`lb://inventory-service`) rather than by host.

---

## Service responsibilities

| Service | Owns | Does not own |
|---|---|---|
| auth | user identity, credentials, tokens | anything about orders |
| product | catalogue, categories, prices | stock levels |
| inventory | stock, reservations, movement audit | product names or prices |
| order | order lifecycle and status history | stock, money |
| payment | payment records, gateway interaction | order status |
| notification | notification history | any business decision |

The split is by **business capability**, not by technical layer. The test is whether a
service can be changed and deployed without coordinating with another. Adding SMS
notifications touches only notification-service, which is why that service exists as
a separate deployable at all.

---

## Communication patterns

| Call | Style | Why |
|---|---|---|
| order → product | synchronous (Feign) | Need the price now, to build the order |
| order → inventory (reserve) | synchronous (Feign) | Customer needs an immediate yes/no |
| order → payment | **asynchronous** (Kafka) | Provider latency must not block checkout |
| payment → order | asynchronous (Kafka) | Order reacts to an outcome |
| payment → inventory | asynchronous (Kafka) | Compensation, no reply needed |
| anything → notification | asynchronous (Kafka) | Fire and forget |

The rule: **synchronous when the caller cannot proceed without the answer, asynchronous
otherwise.** Every synchronous hop is a coupling and a shared failure mode, so there are
only two of them, both on the path where the customer is waiting.

Note what is *not* here: nobody calls notification-service. It was added by subscribing
to topics that already existed, without a line of change in order or payment. That is
the concrete payoff of the event-driven design.

---

## The order saga

Order creation spans two databases owned by two services, so there is no ACID
transaction available. It is a saga: a sequence of local transactions, each with a
compensating action.

| # | Step | Local transaction | Compensation |
|---|---|---|---|
| 1 | Price the order | read product catalogue | none needed |
| 2 | Persist the order | `order_db` insert | rollback (same transaction) |
| 3 | Reserve stock | `inventory_db` update | release stock |
| 4 | Charge | `payment_db` insert | refund (not implemented) |
| 5 | Confirm | `order_db` update | — |

**Orchestration vs choreography.** Steps 1–3 are *orchestrated* by order-service,
because the customer is waiting on a single synchronous response and someone has to own
that decision. Steps 4–5 are *choreographed*: payment-service publishes an outcome, and
order-service and inventory-service each react independently. Neither is universally
better — orchestration gives you one readable place to look when the flow is
interactive, choreography gives you decoupling when it is not.

**The compensation path in practice.** When payment is declined:
1. payment-service writes `FAILED` and publishes `PaymentFailed`.
2. inventory-service consumes it and returns the reserved quantity to available.
3. order-service consumes it and moves the order to `PAYMENT_FAILED`.
4. notification-service consumes it and tells the customer.

Steps 2, 3 and 4 are independent. If inventory-service is down when the event is
published, Kafka retains it and the release happens when it comes back. Nothing is lost
and no orchestrator has to track it.

---

## Transaction boundaries

Each service uses a local ACID transaction for its own work:

- **order-service** — order row, its items and the status-history row all commit
  together. If the inventory reservation throws, the entire transaction rolls back and
  no orphan order is left behind.
- **inventory-service** — the stock update and its audit row commit together. A
  multi-line reservation is all-or-nothing across every line.
- **payment-service** — the payment row commits before any event is published.

What is deliberately **not** in a transaction: any remote call. Holding a database
transaction open across a network call means holding row locks for the duration of
someone else's outage.

---

## Consistency and the dual-write problem

Writing to the database and publishing to Kafka are two separate systems. Do them
naively and you get a dual write:

```java
orderRepository.save(order);          // committed
kafkaTemplate.send("order-created");  // ...then the process dies
```

or worse, publishing inside a transaction that later rolls back — payment then charges
for an order that does not exist.

**What this system does.** Events are published from
`@TransactionalEventListener(phase = AFTER_COMMIT)`. The service raises a Spring
application event inside the transaction; the Kafka send happens only if the transaction
commits. A rollback publishes nothing.

**What that still does not solve.** If the commit succeeds and the broker is unreachable
immediately afterwards, the event is lost. Closing that requires a **transactional
outbox**: write the event to an `outbox` table in the same transaction, and have a
separate relay poll that table and publish. That is the honest next step and it is not
implemented here — it is listed in the README's future enhancements rather than claimed.

**Consistency window.** Between "order created" and "payment settled" the system is
eventually consistent: stock is held, the order says `PAYMENT_PENDING`, and no money has
moved. Typical duration is under a second. The window is bounded because payment either
succeeds or fails; an order stuck there because an event was never delivered needs a
timeout sweeper, which is also listed as future work.

---

## Idempotency

Kafka is at-least-once. After a consumer restart or a partition rebalance, an event that
was already handled can be redelivered. Every consumer therefore has to be idempotent —
and this system uses three different mechanisms, chosen to fit each case.

**1. A database unique constraint — payment-service.**
`payments.order_id` is `UNIQUE`. Two concurrent deliveries of `OrderCreated` can both
pass an application-level "does a payment exist?" check, because both read before either
writes. Only one can insert. The constraint is the guarantee; the `findByOrderId` check
is just a fast path that avoids a pointless exception.

**2. An explicit ledger — order-service.**
A `processed_events` table keyed by `event_id`. The consumer records the id before doing
the work and skips anything already present. Used here because the work
(status transition, publishing a further event) has no natural unique key of its own.

**3. Existing state — inventory-service.**
The audit trail already answers the question: "is there a RESERVE transaction for this
order?" No extra table is needed, because the domain already records what happened.

The general point: idempotency is a design requirement of any at-least-once system, not
a bug fix. It is cheapest when some existing key or state already encodes "this was
done".

---

## Concurrency and locking

**The overselling race.** One unit left, two orders arrive simultaneously:

```
T1: read available = 1        T2: read available = 1
T1: 1 >= 1, ok                T2: 1 >= 1, ok
T1: write available = 0       T2: write available = 0
```

Both succeed. One unit sold twice. Optimistic locking does not fully solve this either —
it converts the second write into a failure the caller must retry, which is worse
ergonomics on a hot row.

**The fix.** `InventoryRepository.findByProductIdForUpdate` uses
`@Lock(PESSIMISTIC_WRITE)`, which issues `SELECT … FOR UPDATE`. The second transaction
blocks until the first commits, then reads `available = 0` and is correctly rejected.

**Deadlock avoidance.** Two orders containing products A and B in opposite order would
deadlock: T1 holds A wanting B, T2 holds B wanting A. The reserve path therefore sorts
items by product id before locking, so every transaction acquires locks in the same
order and a cycle is impossible.

**Optimistic locking elsewhere.** Every other mutable entity carries `@Version`.
Contention is low there and optimistic locking costs nothing when there is no conflict.

**Rule of thumb applied here:** pessimistic locking where contention is expected and a
conflict is expensive (stock); optimistic everywhere else.

---

## Caching

Cache-aside on the product catalogue, which is read far more than it is written.

- `@Cacheable` on `getById`; 10-minute TTL; JSON serialization.
- `@CacheEvict` on update and delete.

**Why eviction and not just a TTL.** With a TTL alone, a price change would be invisible
for up to ten minutes and customers would be quoted a stale price. Eviction makes the
change visible on the next read. The TTL is a backstop against drift, not the
correctness mechanism.

**Why the DTO and not the entity.** Caching a JPA entity serialises Hibernate proxies
and lazy-loading state; deserialising it later gives you an object detached from any
session with fields that explode on access.

**Why nulls are not cached.** Otherwise a lookup for a product that does not exist yet
poisons the cache with a 404 for the full TTL.

**What is not cached.** Stock levels and order state. Both change constantly and a stale
read is a correctness bug, not a performance win.

---

## Fault tolerance

| Failure | Behaviour |
|---|---|
| product-service down | Order creation fails fast; no order row created |
| inventory-service down | Circuit breaker opens; `503`; no order accepted |
| payment-service down | Orders still accepted; events queue in Kafka; processed on recovery |
| notification-service down | No customer impact; backlog consumed on recovery |
| Kafka down | Synchronous paths still work; publishes fail and are logged |
| A database down | Only the owning service is affected |
| Poison message | 3 retries, then routed to `<topic>.DLT`; the partition keeps moving |

The asymmetry is the point: an outage in payment or notification degrades the system,
while an outage in inventory stops it. That is a deliberate consequence of what is
synchronous and what is not.

**Why the fallback fails instead of succeeding.** `InventoryClientFacade`'s fallback
throws `ServiceUnavailableException` rather than returning a fake reservation. A fallback
that returned success would let the order proceed to payment for stock that was never
held — a silent data-integrity bug that surfaces days later at fulfilment. When the truth
is unknown, an error is the correct answer.

---

## Security

Defence in depth. The gateway verifies the JWT and rejects bad traffic at the edge, and
**every service verifies the token again independently**. If a service is reached
directly inside the cluster — a misconfigured NetworkPolicy, a compromised pod — it is
still protected. Trusting a gateway-set `X-User-Id` header alone would make that header
a forgeable credential.

- HS256 JWTs with a `type` claim, so a refresh token cannot be replayed as an access token.
- BCrypt password hashing (adaptive, salted).
- Authorization at two levels: URL rules for coarse access, `@PreAuthorize` and explicit
  ownership checks in the service layer for "may this user touch *this* row".
- Identical error for a wrong password and an unknown username, so login cannot be used
  to enumerate accounts.
- The signing secret comes from the environment and the service refuses to start if it is
  missing or under 32 bytes — a misconfiguration fails loudly at boot rather than quietly
  producing forgeable tokens.

---

## Observability

A single customer action touches up to five services. Without a shared identifier,
debugging means correlating timestamps by hand.

The gateway generates `X-Correlation-Id` (or honours an inbound one), forwards it, and
each service puts it into the SLF4J MDC so every log line carries it. Feign propagates
it on outbound calls; events carry it in the envelope. It is also returned in every API
response as `traceId`, so a user can quote it in a bug report and it leads straight to
the relevant logs across all nine services.

Actuator exposes health (with separate liveness and readiness probes), metrics and a
Prometheus endpoint. Resilience4j registers circuit-breaker state as a health indicator,
so an open breaker is visible without reading logs.

**What is missing:** real distributed tracing. Correlation ids tell you *which* logs
belong together; they do not give you spans, timings, or a waterfall. OpenTelemetry plus
Jaeger is the next step.

---

## Scalability

**Stateless services.** Every service holds no session state, so scaling is horizontal
and a load balancer needs no stickiness.

**Kafka partitioning.** Three partitions per topic, keyed by order id. All events for
one order go to one partition and are processed in order; different orders process in
parallel. Consumer group size can grow up to the partition count.

**Database.** The first bottleneck. Read replicas for catalogue and order history;
partition `orders` by month once it grows; the indexes on `customer_id`, `status` and
`created_at` exist because those are the query shapes the API actually issues.

**Cache.** Absorbs catalogue reads, which are the highest-volume request type.

**Where it would break first, in order:** the PostgreSQL instance shared by all six
logical databases; then the synchronous inventory hop under a hot-product flash sale
(the row lock serialises everything for that product); then Kafka consumer lag on the
notification service.

---

## Known limitations

Stated plainly, because pretending otherwise is worse than the limitation:

1. **No transactional outbox.** A commit followed immediately by a broker outage loses
   the event.
2. **No saga timeout.** An order sits in `PAYMENT_PENDING` forever if a payment event is
   never delivered. A scheduled sweeper should cancel and compensate after a deadline.
3. **The payment gateway is simulated.** Deterministic and honest about it, but not a
   real provider integration.
4. **No refund path.** `REFUNDED` exists in the enum; the flow is not implemented.
5. **One PostgreSQL instance.** Logically separated databases, physically shared — a
   single point of failure that production would split.
6. **No distributed tracing spans**, only correlation ids.
7. **Notification delivery is logged, not sent.** Deliberate: real delivery would require
   third-party credentials and break "clone and run".

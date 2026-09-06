# Contributing

## Getting set up

```bash
git clone https://github.com/rahulray281528-star/enterprise-order-platform.git
cd enterprise-order-platform
docker compose up postgres redis kafka   # infrastructure only
mvn clean install
```

Run an individual service from your IDE, or the whole platform with
`docker compose up --build`.

## Before opening a pull request

```bash
mvn clean verify -Pquality
```

This runs unit tests, integration tests, Checkstyle, SpotBugs and coverage — the same
checks GitHub Actions runs.

## Conventions

- **Java 21**, 4-space indent, 120-column limit (see `.editorconfig`).
- **Never expose a JPA entity from a controller.** Map to a DTO.
- **Every new endpoint needs** `@Valid` on its request body, an `@Operation` summary, and
  an authorization rule.
- **Schema changes go in a new Liquibase changeset.** Never edit an applied one — the
  checksum will not match and every existing environment will refuse to start.
- **Kafka consumers must be idempotent.** Delivery is at-least-once; assume every event
  will arrive twice.
- **Publish events after the transaction commits**, not inside it.
- **Tests should cover behaviour worth protecting**, not raise a coverage number.

## Commit messages

```
<type>(<scope>): <subject>

feat(order): add partial cancellation for multi-line orders
fix(inventory): release stock when an order is cancelled before payment
docs(readme): document the payment failure walkthrough
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `perf`.

## Reporting a bug

Include the correlation id (`traceId`) from the error response — it ties the failure to
log lines across every service.

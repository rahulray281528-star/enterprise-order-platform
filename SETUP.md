# Setup guide

The short version lives in the [README](README.md#quick-start). This file is the
detailed walkthrough, including running without Docker.

---

## Option 1 — Docker Compose (recommended)

### What you need

| Software | Minimum | Verify |
|---|---|---|
| Docker Engine | 24+ | `docker --version` |
| Docker Compose | v2 | `docker compose version` |

Java and Maven are **not** needed — the build happens inside a container.

**Give Docker at least 6 GB of RAM.** Twelve containers will not fit in the 2 GB
default. Docker Desktop → Settings → Resources → Memory.

### Steps

```bash
git clone https://github.com/rahulray281528-star/enterprise-order-platform.git
cd enterprise-order-platform
cp .env.example .env        # optional
docker compose up --build
```

The first build downloads the Maven dependency tree and takes 5–10 minutes. Later starts
take about a minute.

### Confirm it worked

```bash
docker compose ps                                    # all services "healthy"
curl http://localhost:8080/actuator/health           # {"status":"UP"}
open http://localhost:8761                           # 9 instances registered in Eureka
```

Then follow the [walkthrough](README.md#walkthrough-place-an-order-end-to-end).

---

## Option 2 — run services from your IDE

Useful when you want to debug a single service.

### What you need

| Software | Minimum | Verify |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | 24+ | for PostgreSQL, Redis and Kafka |

### Steps

```bash
# 1. infrastructure only
docker compose up postgres redis kafka

# 2. build everything once (installs the common module into your local repo)
mvn clean install -DskipTests

# 3. start services in this order, each in its own terminal
mvn -pl service-discovery spring-boot:run
mvn -pl config-server     spring-boot:run
mvn -pl auth-service      spring-boot:run
mvn -pl product-service   spring-boot:run
mvn -pl inventory-service spring-boot:run
mvn -pl order-service     spring-boot:run
mvn -pl payment-service   spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl api-gateway       spring-boot:run
```

Order matters only for service-discovery — everything else retries registration.

The default `application.yml` in each service already points at `localhost` for
PostgreSQL, Redis and Kafka, so no extra configuration is needed.

### Databases

`docker/init-databases.sh` creates all six databases the first time the PostgreSQL
container starts. If you started PostgreSQL some other way, create them by hand:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
```

Liquibase creates the tables and seed data on each service's first start.

---

## Verifying the build

```bash
mvn clean install            # compile + unit tests
mvn clean verify             # + integration tests (Testcontainers, needs Docker)
mvn clean verify -Pquality   # + Checkstyle and SpotBugs
```

---

## Common problems

See [Troubleshooting](README.md#troubleshooting) in the README.

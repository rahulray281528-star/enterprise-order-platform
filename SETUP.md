# LOCAL SETUP GUIDE

## Prerequisites

Before running this project, ensure you have:

### Required Software
- **Java 21 (JDK 21)** - [Download](https://www.oracle.com/java/technologies/downloads/#java21)
  ```bash
  java -version
  # Should show: java version "21.x.x"
  ```

- **Apache Maven 3.8+** - [Download](https://maven.apache.org/download.cgi)
  ```bash
  mvn -version
  # Should show: Apache Maven 3.8.x or higher
  ```

- **Docker** - [Download](https://www.docker.com/products/docker-desktop)
  ```bash
  docker --version
  docker-compose --version
  ```

- **Git** - [Download](https://git-scm.com/downloads)
  ```bash
  git --version
  ```

### Optional but Recommended
- **IDE**: IntelliJ IDEA, VS Code, or Eclipse
- **Postman** or **Thunder Client** - For API testing
- **MySQL Workbench** or **pgAdmin** - Database GUI (optional)

---

## Step 1: Clone Repository

```bash
git clone https://github.com/rahulray281528-star/enterprise-order-platform.git
cd enterprise-order-platform
```

---

## Step 2: Configure Environment Variables

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your values (optional for local dev)
# Most defaults work for local development
```

**Key Environment Variables:**
```
JWT_SECRET=your-super-secret-jwt-key-change-in-production-min-32-chars-long-key-123456
POSTGRES_PASSWORD=postgres123
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
REDIS_HOST=redis
```

---

## Step 3: Build the Project

```bash
# Full build with tests
mvn clean install

# Or skip tests for faster build
mvn clean install -DskipTests

# Build specific module
cd auth-service
mvn clean install
```

**Expected Output:**
```
[INFO] Building com.enterprise:enterprise-order-platform:1.0.0
[INFO] ----------------------------------------
[INFO] Building com.enterprise:common:1.0.0
[INFO] BUILD SUCCESS
...
[INFO] Total time: 2 min 15 sec
```

---

## Step 4: Start Infrastructure with Docker

```bash
# Start all services
docker-compose up --build -d

# View logs
docker-compose logs -f

# Check services status
docker-compose ps
```

**Expected Services:**
```
CONTAINER ID   IMAGE                              STATUS
xxx            postgres:15-alpine                 Up (healthy)
xxx            redis:7-alpine                     Up (healthy)
xxx            confluentinc/cp-kafka:7.5.0       Up (healthy)
xxx            enterprise-service-discovery:...   Up (healthy)
xxx            enterprise-auth-service:...        Up (healthy)
xxx            enterprise-product-service:...     Up (healthy)
xxx            enterprise-inventory-service:...   Up (healthy)
xxx            enterprise-order-service:...       Up (healthy)
xxx            enterprise-payment-service:...     Up (healthy)
xxx            enterprise-notification-service:.. Up (healthy)
xxx            enterprise-api-gateway:...         Up (healthy)
```

**Wait 2-3 minutes** for all services to start and register with Eureka.

---

## Step 5: Verify Services are Running

### Check Service Discovery (Eureka)
```bash
curl http://localhost:8761

# Should return HTML dashboard
```

Access in browser: **http://localhost:8761**

### Check API Gateway Health
```bash
curl http://localhost:8080/actuator/health

# Should return:
# {"status":"UP","components":{...}}
```

### Check Individual Services
```bash
curl http://localhost:8081/actuator/health  # Auth Service
curl http://localhost:8082/actuator/health  # Product Service
curl http://localhost:8083/actuator/health  # Inventory Service
curl http://localhost:8084/actuator/health  # Order Service
curl http://localhost:8085/actuator/health  # Payment Service
curl http://localhost:8086/actuator/health  # Notification Service
```

---

## Step 6: Test API Endpoints

### 1. Register a User (Customer)

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "customer1",
    "email": "customer1@example.com",
    "password": "Password@123",
    "role": "CUSTOMER"
  }'
```

**Response:**
```json
{
  "status": 201,
  "message": "User registered successfully",
  "data": {
    "id": "uuid-xxx",
    "username": "customer1",
    "email": "customer1@example.com",
    "role": "CUSTOMER",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 2. Login (Get JWT Token)

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "customer1",
    "password": "Password@123"
  }'
```

**Response:**
```json
{
  "status": 200,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjdXN0b21lcjEi...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjdXN0b21lcjEi...",
    "expiresIn": 3600,
    "tokenType": "Bearer"
  },
  "timestamp": "2024-01-15T10:31:00Z"
}
```

**Save the `accessToken`** for next requests.

### 3. Create Product (Admin)

First, register as ADMIN:
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin1",
    "email": "admin1@example.com",
    "password": "AdminPass@123",
    "role": "ADMIN"
  }'
```

Login as admin and use that token, then:

```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 1299.99,
    "categoryId": 1,
    "sku": "LAP-001",
    "stock": 50
  }'
```

### 4. Get All Products

```bash
curl http://localhost:8080/api/v1/products?page=0&size=20

# Response:
{
  "status": 200,
  "message": "Products retrieved successfully",
  "data": {
    "content": [...],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

### 5. Create Order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_CUSTOMER_TOKEN" \
  -d '{
    "items": [
      {
        "productId": "PRODUCT_UUID",
        "quantity": 1
      }
    ]
  }'
```

### 6. Get Order Status

```bash
curl http://localhost:8080/api/v1/orders/ORDER_UUID \
  -H "Authorization: Bearer YOUR_CUSTOMER_TOKEN"
```

---

## Step 7: View Swagger API Documentation

Once services are running, access Swagger UI:

- **API Gateway:** http://localhost:8080/swagger-ui.html
- **Auth Service:** http://localhost:8081/swagger-ui.html
- **Product Service:** http://localhost:8082/swagger-ui.html
- **Inventory Service:** http://localhost:8083/swagger-ui.html
- **Order Service:** http://localhost:8084/swagger-ui.html
- **Payment Service:** http://localhost:8085/swagger-ui.html
- **Notification Service:** http://localhost:8086/swagger-ui.html

---

## Step 8: View Logs and Monitoring

### Follow All Logs
```bash
docker-compose logs -f
```

### Follow Specific Service Logs
```bash
docker-compose logs -f order-service
docker-compose logs -f payment-service
docker-compose logs -f auth-service
```

### Monitor Kafka Topics
```bash
# List topics
docker exec enterprise-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor order-created-events
docker exec enterprise-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created-events \
  --from-beginning
```

### Access PostgreSQL
```bash
# Connect to PostgreSQL
docker exec -it enterprise-postgres psql -U postgres -d order_platform

# List tables
\dt

# View users
SELECT * FROM users;

# Exit
\q
```

### Access Redis
```bash
# Connect to Redis
docker exec -it enterprise-redis redis-cli

# Get all keys
KEYS *

# Get specific key
GET product:1

# Exit
EXIT
```

---

## Step 9: Run Tests

### Run All Tests
```bash
mvn clean test
```

### Run Integration Tests
```bash
mvn clean verify
```

### Run Specific Test
```bash
mvn test -Dtest=OrderServiceTest
```

### Generate Coverage Report
```bash
mvn jacoco:report
open target/site/jacoco/index.html
```

---

## Step 10: Code Quality Checks

```bash
# Checkstyle
mvn checkstyle:check

# SpotBugs
mvn spotbugs:check

# All checks
mvn clean verify
```

---

## Troubleshooting

### Issue: Port Already in Use

```bash
# Kill process on port 8080
lsof -i :8080
kill -9 <PID>

# Or change port in docker-compose.yml
```

### Issue: PostgreSQL Connection Refused

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Restart PostgreSQL
docker-compose restart postgres

# Check logs
docker-compose logs postgres
```

### Issue: Kafka Connection Issues

```bash
# Verify Kafka is healthy
docker-compose ps kafka

# Check Kafka logs
docker-compose logs kafka

# List topics to verify Kafka is responsive
docker exec enterprise-kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Issue: Services Not Registering with Eureka

```bash
# Wait 30-60 seconds for all services to start
# Check Eureka dashboard: http://localhost:8761

# If services still not registered:
docker-compose logs service-discovery
docker-compose logs auth-service
```

### Issue: Maven Build Fails

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean install

# Or build without tests
mvn clean install -DskipTests
```

### Issue: Docker Compose Won't Start

```bash
# Pull latest images
docker-compose pull

# Remove old containers
docker-compose down -v

# Rebuild
docker-compose up --build -d
```

---

## Performance Tips

### Reduce Build Time
```bash
# Skip tests during development
mvn clean install -DskipTests

# Use daemon mode
mvn -T 1C clean install  # Use 1 core per thread
```

### Faster Docker Builds
```bash
# Use BuildKit
DOCKER_BUILDKIT=1 docker-compose up --build -d
```

### Database Performance
```bash
# Check slow queries
docker exec enterprise-postgres psql -U postgres -d order_platform -c "SELECT * FROM pg_stat_statements;"
```

---

## Useful Commands Reference

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (reset database)
docker-compose down -v

# Rebuild specific service
docker-compose up --build -d auth-service

# View environment variables
docker-compose config

# Restart a service
docker-compose restart order-service

# Create a backup of PostgreSQL
docker exec enterprise-postgres pg_dump -U postgres order_platform > backup.sql

# Restore from backup
docker exec -i enterprise-postgres psql -U postgres order_platform < backup.sql

# Check JVM memory usage
docker stats enterprise-order-service
```

---

## Next Steps

1. **Explore Swagger UI** - Test APIs interactively
2. **Review Source Code** - Understand microservices architecture
3. **Read System Design Docs** - See `docs/system-design.md`
4. **Run Integration Tests** - `mvn verify`
5. **Deploy to Kubernetes** - See `kubernetes/README.md`

---

## Getting Help

- **Check logs**: `docker-compose logs -f <service-name>`
- **Review README.md** - Main project documentation
- **Check docs/ folder** - Architecture and design docs
- **GitHub Issues** - Report bugs or ask questions

---

**Happy coding! 🚀**

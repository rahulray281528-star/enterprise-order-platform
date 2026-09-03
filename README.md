# Enterprise Order Management Platform

A production-grade microservices platform demonstrating enterprise Java development with Spring Boot 3, Kafka, PostgreSQL, Redis, Kubernetes, and comprehensive DevOps practices.

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway (Spring Cloud Gateway)        │
│              Routes & Authentication Integration             │
└────────────────┬────────────────────────────────────────────┘
                 │
    ┌────────────┼────────────────┬──────────────────┬──────────────┐
    │            │                │                  │              │
    ▼            ▼                ▼                  ▼              ▼
┌─────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐ ┌─────────────┐
│  Auth   │ │ Product  │ │  Inventory   │ │    Order     │ │  Payment    │
│ Service │ │ Service  │ │   Service    │ │   Service    │ │  Service    │
└────┬────┘ └────┬─────┘ └──────┬───────┘ └──────┬───────┘ └──────┬──────┘
     │           │              │                │                │
     └───────────┼──────────────┼────────────────┼────────────────┘
                 │
                 ▼
         ┌──────────────────┐
         │  Notification    │
         │    Service       │
         └──────────────────┘
                 ▲
                 │
         ┌───────┴──────────┐
         │                  │
    ┌────▼────┐    ┌────────▼───┐
    │ Kafka   │    │  Event Bus  │
    │ Topics  │    │  (Redis)    │
    └─────────┘    └─────────────┘

Data Layer:
┌────────────┬──────────────┬──────────────┬───────────┬──────────────┐
│PostgreSQL  │ PostgreSQL   │ PostgreSQL   │PostgreSQL │ PostgreSQL   │
│Auth DB     │ Product DB   │Inventory DB  │ Order DB  │ Payment DB   │
└────────────┴──────────────┴──────────────┴───────────┴──────────────┘
```

## 📋 Features

### Authentication & Authorization
- JWT token-based authentication
- Spring Security with role-based access control (RBAC)
- BCrypt password hashing
- Separate roles: CUSTOMER, ADMIN
- Token refresh mechanism

### Order Management
- Complete order lifecycle: CREATED → INVENTORY_RESERVED → PAYMENT_PENDING → CONFIRMED → COMPLETED
- Failure scenarios: PAYMENT_FAILED, CANCELLED
- Order status tracking and history
- Order retrieval by customer and ID

### Product Management
- CRUD operations for products
- Category management
- Product search and filtering
- Pagination and sorting
- Redis caching for frequently accessed products

### Inventory Management
- Real-time inventory tracking
- Inventory reservation system
- Transaction history and audit trail
- Optimistic locking for consistency
- Inventory release on order cancellation

### Payment Processing
- Payment gateway integration patterns
- Multiple payment statuses: INITIATED, PENDING, COMPLETED, FAILED
- Idempotent payment processing
- Payment history and reconciliation

### Event-Driven Architecture
Kafka topics for asynchronous communication:
- `order-created-events`
- `inventory-reserved-events`
- `inventory-reservation-failed-events`
- `payment-initiated-events`
- `payment-completed-events`
- `payment-failed-events`
- `order-confirmed-events`
- `order-cancelled-events`
- `notification-events`

### Resilience & Fault Tolerance
- Circuit breaker pattern (Resilience4j)
- Retry mechanisms with exponential backoff
- Timeout handling
- Fallback strategies
- Dead-letter queues for failed events

### Caching Strategy
- Redis-based caching for products
- Cache invalidation on updates
- Distributed cache coherence

### Observability
- Spring Boot Actuator with custom endpoints
- Structured logging with correlation IDs
- Request tracing through microservices
- Health checks and readiness probes
- Metrics collection

### Code Quality
- JUnit 5 unit tests
- Mockito for mocking
- Testcontainers for integration tests
- JaCoCo code coverage
- Checkstyle code style enforcement
- SpotBugs static analysis

## 🛠️ Technology Stack

### Core
- **Java 21** - Latest LTS version with virtual threads support
- **Spring Boot 3.2.0** - Latest stable version
- **Spring Cloud 2023.0.0** - Microservices capabilities

### Databases
- **PostgreSQL 14+** - Relational database
- **Liquibase 4.24.0** - Schema versioning and migrations

### Messaging & Caching
- **Apache Kafka 3.6+** - Event streaming (KRaft mode, no Zookeeper)
- **Redis 7.0+** - In-memory caching

### Service Communication
- **Spring Cloud Gateway** - API Gateway
- **Spring Cloud Service Discovery** - Eureka
- **Spring Cloud Config Server** - Centralized configuration
- **OpenFeign** - Declarative HTTP client

### Security
- **Spring Security 6.2** - Authentication & authorization
- **JWT (JJWT 0.12.3)** - Token-based authentication

### Resilience
- **Resilience4j 2.1.0** - Circuit breaker, retry, timeout

### Testing
- **JUnit 5** - Testing framework
- **Mockito 5.2** - Mocking framework
- **Testcontainers 1.19.3** - Container-based integration tests

### Monitoring & Quality
- **Spring Boot Actuator** - Health, metrics, insights
- **JaCoCo 0.8.10** - Code coverage
- **Checkstyle 10.12.1** - Code style
- **SpotBugs 4.7.3** - Bug detection

### DevOps
- **Docker & Docker Compose** - Containerization
- **Kubernetes** - Orchestration
- **Jenkins** - CI/CD pipeline

## 🏢 Microservices

### 1. Service Discovery (Eureka)
**Port:** 8761

Service registry for all microservices to register and discover each other.

```
GET /eureka/apps
```

### 2. Config Server
**Port:** 8888

Centralized configuration management for all services.

```
GET /config-server/default
```

### 3. API Gateway
**Port:** 8080

Entry point for all API requests. Routes to appropriate services.

```
Endpoints:
GET  /api/v1/auth/login
POST /api/v1/auth/register
GET  /api/v1/products
GET  /api/v1/orders/{orderId}
```

### 4. Auth Service
**Port:** 8081
**Database:** PostgreSQL (auth_db)

User authentication and authorization.

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
GET    /api/v1/auth/validate
GET    /api/v1/users/{userId}
```

### 5. Product Service
**Port:** 8082
**Database:** PostgreSQL (product_db)
**Cache:** Redis

Product catalog management.

```
GET    /api/v1/products
GET    /api/v1/products/{id}
POST   /api/v1/products (ADMIN)
PUT    /api/v1/products/{id} (ADMIN)
DELETE /api/v1/products/{id} (ADMIN)
GET    /api/v1/categories
```

### 6. Inventory Service
**Port:** 8083
**Database:** PostgreSQL (inventory_db)

Real-time inventory management.

```
GET    /api/v1/inventory/{productId}
POST   /api/v1/inventory/reserve
POST   /api/v1/inventory/release
GET    /api/v1/inventory/transactions
```

### 7. Order Service
**Port:** 8084
**Database:** PostgreSQL (order_db)

Order lifecycle management.

```
POST   /api/v1/orders
GET    /api/v1/orders/{orderId}
GET    /api/v1/orders/customer/{customerId}
PUT    /api/v1/orders/{orderId}/cancel
GET    /api/v1/orders (pagination)
```

### 8. Payment Service
**Port:** 8085
**Database:** PostgreSQL (payment_db)

Payment processing and reconciliation.

```
POST   /api/v1/payments
GET    /api/v1/payments/{paymentId}
POST   /api/v1/payments/{paymentId}/refund
GET    /api/v1/payments/order/{orderId}
```

### 9. Notification Service
**Port:** 8086

Asynchronous notification delivery (email, SMS, in-app).

```
POST   /api/v1/notifications/email
POST   /api/v1/notifications/sms
GET    /api/v1/notifications/history
```

## 📊 Database Design

### Auth Service
```
users (id, username, email, password_hash, created_at)
roles (id, name)
user_roles (user_id, role_id)
```

### Product Service
```
categories (id, name, description)
products (id, name, description, price, category_id, created_at)
```

### Inventory Service
```
inventory (id, product_id, quantity, reserved_quantity, last_updated)
inventory_transactions (id, product_id, transaction_type, quantity, created_at)
```

### Order Service
```
orders (id, customer_id, total_amount, status, created_at, updated_at)
order_items (id, order_id, product_id, quantity, price)
order_status_history (id, order_id, old_status, new_status, created_at)
```

### Payment Service
```
payments (id, order_id, amount, status, method, created_at)
payment_transactions (id, payment_id, transaction_type, amount, status)
```

## 🔐 Security Architecture

### Authentication Flow
```
User Login
    ↓
Credentials Validation (Auth Service)
    ↓
JWT Token Generation (access + refresh tokens)
    ↓
Token returned to client
    ↓
Client includes token in Authorization header
    ↓
API Gateway validates token
    ↓
Request routed to target service
```

### Authorization
- API Gateway performs initial authentication
- Services validate JWT claims for authorization
- Role-based access control on protected endpoints
- Admin endpoints require ADMIN role
- Customer endpoints require CUSTOMER role

### Secrets Management
- JWT secret stored in environment variables
- Database passwords in `.env` file (not committed)
- Spring Cloud Config can encrypt sensitive properties
- No hardcoded credentials in source code

## 📡 Kafka Event Architecture

### Event Flow Example: Order Creation

```
1. Order Service creates order
   ├─ Sets status to CREATED
   └─ Publishes OrderCreatedEvent

2. OrderCreatedEvent consumed by:
   ├─ Inventory Service
   │  └─ Reserves inventory
   │     └─ Publishes InventoryReservedEvent
   │
   └─ Notification Service
      └─ Sends confirmation notification

3. InventoryReservedEvent consumed by:
   ├─ Order Service
   │  └─ Updates order status to INVENTORY_RESERVED
   │     └─ Publishes InventoryReservedEvent
   │
   └─ Payment Service
      └─ Initiates payment
         └─ Publishes PaymentInitiatedEvent

4. PaymentCompletedEvent consumed by:
   ├─ Order Service
   │  └─ Updates order status to CONFIRMED
   │     └─ Publishes OrderConfirmedEvent
   │
   └─ Notification Service
      └─ Sends order confirmation email

5. Failure scenarios with retries and DLQ
```

### Topics
| Topic | Producer | Consumers | Purpose |
|-------|----------|-----------|---------|
| `order-created-events` | Order Service | Inventory, Notification | New order created |
| `inventory-reserved-events` | Inventory Service | Order, Payment | Inventory reserved |
| `inventory-reservation-failed` | Inventory Service | Order, Notification | Inventory unavailable |
| `payment-initiated-events` | Payment Service | Order, Notification | Payment started |
| `payment-completed-events` | Payment Service | Order, Notification | Payment successful |
| `payment-failed-events` | Payment Service | Order, Notification | Payment failed |
| `order-confirmed-events` | Order Service | Notification | Order confirmed |
| `order-cancelled-events` | Order Service | Inventory, Notification | Order cancelled |
| `notification-events` | All services | Notification | Generic notifications |
| `*.dlt` | All services | Error handling | Dead-letter topics |

## ⚙️ Resilience Patterns

### Circuit Breaker
```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
```

### Retry
```yaml
resilience4j:
  retry:
    instances:
      payment-service:
        maxAttempts: 3
        waitDuration: 1000
        retryExceptions:
          - java.net.SocketTimeoutException
```

### Timeout
```yaml
resilience4j:
  timelimiter:
    instances:
      payment-service:
        timeoutDuration: 5s
        cancelRunningFuture: true
```

## 🚀 Getting Started

### Prerequisites
- JDK 21+
- Maven 3.8+
- Docker & Docker Compose
- Git

### Local Setup

#### 1. Clone Repository
```bash
git clone https://github.com/rahulray281528-star/enterprise-order-platform.git
cd enterprise-order-platform
```

#### 2. Build Project
```bash
mvn clean install -DskipTests
```

#### 3. Start Infrastructure
```bash
docker compose up --build -d
```

This starts:
- PostgreSQL (5432)
- Redis (6379)
- Kafka & KRaft Controller (9092)
- Zookeeper UI (8888)

#### 4. Wait for Services
```bash
# Check service health
docker compose ps

# View logs
docker compose logs -f
```

#### 5. Verify Setup
```bash
# Service Discovery (Eureka)
curl http://localhost:8761

# API Gateway Health
curl http://localhost:8080/actuator/health

# Auth Service Health
curl http://localhost:8081/actuator/health
```

### Environment Variables

Create `.env` file in project root:
```env
# PostgreSQL
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres123
POSTGRES_DB=order_platform

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# Kafka
KAFKA_BROKERS=kafka:9092
KAFKA_SECURITY_PROTOCOL=PLAINTEXT

# JWT
JWT_SECRET=your-super-secret-jwt-key-change-in-production-at-least-32-characters
JWT_EXPIRATION=3600000

# App Profiles
APP_PROFILE=local
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Generate coverage report
mvn jacoco:report
# View report: target/site/jacoco/index.html
```

### API Documentation

Once services are running, access Swagger/OpenAPI documentation:

- **API Gateway:** http://localhost:8080/swagger-ui.html
- **Product Service:** http://localhost:8082/swagger-ui.html
- **Order Service:** http://localhost:8084/swagger-ui.html
- **Auth Service:** http://localhost:8081/swagger-ui.html

### Sample API Requests

#### 1. Register User
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "customer1",
    "email": "customer1@example.com",
    "password": "password123",
    "role": "CUSTOMER"
  }'
```

Response:
```json
{
  "id": "uuid-1",
  "username": "customer1",
  "email": "customer1@example.com",
  "role": "CUSTOMER",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### 2. Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "customer1",
    "password": "password123"
  }'
```

Response:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

#### 3. Create Product (Admin)
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access-token>" \
  -d '{
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 1299.99,
    "categoryId": 1,
    "sku": "LAP-001"
  }'
```

#### 4. Get Products
```bash
curl http://localhost:8080/api/v1/products?page=0&size=20&sort=name,asc
```

Response:
```json
{
  "content": [
    {
      "id": "uuid-1",
      "name": "Laptop",
      "description": "High-performance laptop",
      "price": 1299.99,
      "stock": 50,
      "sku": "LAP-001"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

#### 5. Create Order
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access-token>" \
  -d '{
    "customerId": "uuid-1",
    "items": [
      {
        "productId": "uuid-1",
        "quantity": 1,
        "price": 1299.99
      }
    ]
  }'
```

Response:
```json
{
  "id": "order-uuid-1",
  "customerId": "uuid-1",
  "status": "CREATED",
  "totalAmount": 1299.99,
  "items": [
    {
      "productId": "uuid-1",
      "quantity": 1,
      "price": 1299.99
    }
  ],
  "createdAt": "2024-01-15T10:35:00Z"
}
```

#### 6. Get Order Status
```bash
curl http://localhost:8080/api/v1/orders/order-uuid-1 \
  -H "Authorization: Bearer <access-token>"
```

Response:
```json
{
  "id": "order-uuid-1",
  "customerId": "uuid-1",
  "status": "CONFIRMED",
  "totalAmount": 1299.99,
  "items": [...],
  "payment": {
    "id": "payment-uuid-1",
    "status": "COMPLETED",
    "method": "CREDIT_CARD"
  },
  "createdAt": "2024-01-15T10:35:00Z",
  "updatedAt": "2024-01-15T10:36:30Z"
}
```

#### 7. Cancel Order
```bash
curl -X PUT http://localhost:8080/api/v1/orders/order-uuid-1/cancel \
  -H "Authorization: Bearer <access-token>"
```

Response:
```json
{
  "id": "order-uuid-1",
  "status": "CANCELLED",
  "cancelledAt": "2024-01-15T10:37:00Z"
}
```

## 📊 Monitoring & Observability

### Health Checks
```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health/details

# Readiness probe
curl http://localhost:8080/actuator/health/readiness

# Liveness probe
curl http://localhost:8080/actuator/health/liveness
```

### Metrics
```bash
# All metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/http.server.requests
```

### Logs
```bash
# View logs with correlation ID
docker compose logs -f order-service | grep "traceId"
```

### Kafka Monitoring
```bash
# List topics
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Describe topic
docker exec kafka kafka-topics.sh --describe --topic order-created-events --bootstrap-server localhost:9092

# Monitor messages
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-created-events --from-beginning
```

## 🧪 Testing Strategy

### Unit Tests
Located in `src/test/java/` within each service.

```bash
mvn test
```

### Integration Tests
Uses Testcontainers for isolated PostgreSQL and Kafka instances.

```bash
mvn verify
```

Example integration test:
```java
@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Test
    void testOrderCreation() {
        // Test complete order creation workflow
    }
}
```

### Test Coverage
```bash
# Generate JaCoCo report
mvn jacoco:report

# View report
open target/site/jacoco/index.html
```

Target coverage: >80% for business logic

## 🐳 Docker & Compose

### Build Individual Service
```bash
cd product-service
docker build -t enterprise-product-service:1.0.0 .
```

### Docker Compose Services
```yaml
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_KRAFT_MODE: true
      CLUSTER_ID: MkQkDjlTQEiZwYkV2hVH1g
    ports:
      - "9092:9092"

  # All services defined...
```

### Health Check Example
```dockerfile
FROM eclipse-temurin:21-jdk

COPY target/product-service-1.0.0.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD java -cp app.jar org.springframework.boot.loader.JarLauncher \
  && curl -f http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## ☸️ Kubernetes Deployment

### Prerequisites
```bash
# Using Minikube
minikube start --cpus=4 --memory=8192

# Or using Kind
kind create cluster --name order-platform
```

### Deploy Services
```bash
# Create namespace
kubectl create namespace order-platform

# Deploy ConfigMap
kubectl apply -f kubernetes/configmap.yaml -n order-platform

# Deploy secrets (Create your own, don't use examples!)
kubectl create secret generic app-secrets \
  --from-literal=db-password=secure-password \
  -n order-platform

# Deploy PostgreSQL
kubectl apply -f kubernetes/postgres-deployment.yaml -n order-platform

# Deploy Redis
kubectl apply -f kubernetes/redis-deployment.yaml -n order-platform

# Deploy Kafka
kubectl apply -f kubernetes/kafka-deployment.yaml -n order-platform

# Deploy services
kubectl apply -f kubernetes/services/ -n order-platform
```

### Verify Deployment
```bash
kubectl get pods -n order-platform
kubectl get services -n order-platform
kubectl logs -f deployment/order-service -n order-platform
```

### Port Forwarding
```bash
kubectl port-forward svc/api-gateway 8080:8080 -n order-platform
```

## 🔄 CI/CD Pipeline

### Jenkins Pipeline
```groovy
pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Compile') {
            steps {
                sh 'mvn clean compile'
            }
        }
        
        stage('Unit Tests') {
            steps {
                sh 'mvn test'
            }
        }
        
        stage('Integration Tests') {
            steps {
                sh 'mvn verify'
            }
        }
        
        stage('Code Quality') {
            steps {
                sh 'mvn checkstyle:check spotbugs:check jacoco:report'
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }
        
        stage('Docker Build') {
            steps {
                sh 'mvn docker:build'
            }
        }
        
        stage('SonarQube Analysis') {
            steps {
                sh 'mvn sonar:sonar'
            }
        }
    }
    
    post {
        always {
            junit '**/target/surefire-reports/*.xml'
            jacoco()
        }
    }
}
```

## ☁️ AWS Architecture

### Deployment Architecture
```
┌─────────────────────────────────────────────────┐
│           AWS Account                           │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌────────────────────────────────────────┐   │
│  │  VPC (10.0.0.0/16)                     │   │
│  ├────────────────────────────────────────┤   │
│  │                                        │   │
│  │  ┌──────────────────────────────────┐ │   │
│  │  │  Public Subnet (ALB)             │ │   │
│  │  │  ┌──────────────────────────────┐│ │   │
│  │  │  │  Application Load Balancer   ││ │   │
│  │  │  └──────────────────────────────┘│ │   │
│  │  └──────────────────────────────────┘ │   │
│  │                 │                      │   │
│  │  ┌──────────────┴──────────────────┐ │   │
│  │  │  Private Subnet (EKS)           │ │   │
│  │  │  ┌──────────────────────────────┐│ │   │
│  │  │  │  EKS Cluster                 ││ │   │
│  │  │  │ ┌─────┬─────┬─────┬─────┐   ││ │   │
│  │  │  │ │Pod1 │Pod2 │Pod3 │Pod4 │   ││ │   │
│  │  │  │ └─────┴─────┴─────┴─────┘   ││ │   │
│  │  │  └──────────────────────────────┘│ │   │
│  │  └──────────────────────────────────┘ │   │
│  │                                        │   │
│  │  ┌──────────────────────────────────┐ │   │
│  │  │  Private Subnet (Database)       │ │   │
│  │  │  ┌──────────────────────────────┐│ │   │
│  │  │  │  RDS PostgreSQL (Multi-AZ)  ││ │   │
│  │  │  └──────────────────────────────┘│ │   │
│  │  └──────────────────────────────────┘ │   │
│  │                                        │   │
│  │  ┌──────────────────────────────────┐ │   │
│  │  │  ElastiCache (Redis)             │ │   │
│  │  │  ┌──────────────────────────────┐│ │   │
│  │  │  │  Multi-node Cluster          ││ │   │
│  │  │  └──────────────────────────────┘│ │   │
│  │  └──────────────────────────────────┘ │   │
│  │                                        │   │
│  │  ┌──────────────────────────────────┐ │   │
│  │  │  MSK (Kafka)                     │ │   │
│  │  │  ┌──────────────────────────────┐│ │   │
│  │  │  │  Multi-broker Cluster        ││ │   │
│  │  │  └──────────────────────────────┘│ │   │
│  │  └──────────────────────────────────┘ │   │
│  │                                        │   │
│  └────────────────────────────────────────┘   │
│                                                 │
│  ┌────────────────────────────────────────┐   │
│  │  S3 Buckets                            │   │
│  │  ├─ artifact-storage                   │   │
│  │  ├─ logs-archive                       │   │
│  │  └─ backup-storage                     │   │
│  └────────────────────────────────────────┘   │
│                                                 │
│  ┌────────────────────────────────────────┐   │
│  │  CloudWatch                            │   │
│  │  ├─ Logs                               │   │
│  │  ├─ Metrics                            │   │
│  │  └─ Alarms                             │   │
│  └────────────────────────────────────────┘   │
│                                                 │
│  ┌────────────────────────────────────────┐   │
│  │  Lambda Functions                      │   │
│  │  ├─ Scheduled maintenance              │   │
│  │  ├─ Event processors                   │   │
│  │  └─ Report generators                  │   │
│  └────────────────────────────────────────┘   │
│                                                 │
└─────────────────────────────────────────────────┘
```

### Key AWS Components

**Compute:**
- EKS (Elastic Kubernetes Service) for container orchestration
- EC2 instances as worker nodes
- Lambda for event-driven processing

**Database:**
- RDS PostgreSQL (Multi-AZ for high availability)
- Multiple read replicas for read scaling

**Caching:**
- ElastiCache Redis cluster for distributed caching

**Messaging:**
- MSK (Managed Streaming for Kafka) for event streaming

**Storage:**
- S3 for artifact storage, logs, backups

**Monitoring:**
- CloudWatch for logs, metrics, dashboards
- X-Ray for distributed tracing

**Networking:**
- VPC with public/private subnets
- ALB (Application Load Balancer)
- NAT Gateway for outbound traffic
- Security Groups for fine-grained access control

**CI/CD:**
- CodePipeline for orchestration
- CodeBuild for compilation and testing
- ECR (Elastic Container Registry) for Docker images

### Cost Optimization
- Auto-scaling based on CPU/memory metrics
- Reserved instances for predictable workloads
- Spot instances for non-critical workloads
- CloudFront CDN for static content

## 📈 Scalability & Performance

### Horizontal Scaling
- Stateless microservices enable easy horizontal scaling
- Kubernetes HPA (Horizontal Pod Autoscaler) automatically scales pods
- Load balancer distributes traffic across instances

### Database Scaling
- Read replicas in PostgreSQL for read-heavy workloads
- Connection pooling to limit database connections
- Query optimization with proper indexing

### Caching Strategy
- Redis distributed cache reduces database load
- Cache-aside pattern for product data
- Cache invalidation on updates

### Kafka Optimization
- Partitioning for parallel processing
- Consumer groups for scalable consumption
- Batch processing for throughput

## 🔍 Troubleshooting

### Service Won't Start
```bash
# Check logs
docker compose logs service-name

# Verify configuration
curl http://localhost:8761/eureka/apps

# Check port availability
lsof -i :8080
```

### Database Connection Issues
```bash
# Verify PostgreSQL is running
docker compose ps postgres

# Check connection
docker compose exec postgres psql -U postgres -d order_platform -c "\dt"

# View logs
docker compose logs postgres
```

### Kafka Message Issues
```bash
# Check topic exists
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor consumer lag
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group order-service --describe

# Replay from DLQ
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-created-events.dlt --from-beginning
```

### Test Failures
```bash
# Run specific test
mvn test -Dtest=OrderServiceTest

# Run integration tests with logs
mvn verify -X

# View test reports
open target/surefire-reports/index.html
```

## 🏆 Interview Discussion Points

### Architecture Decisions
1. **Microservices over Monolith**: Scalability, independent deployment, technology flexibility
2. **Database-per-Service**: Data isolation, independent scaling, reduced coupling
3. **Event-Driven Communication**: Asynchronous decoupling, better resilience
4. **API Gateway**: Centralized routing, authentication, rate limiting

### Resilience Patterns
1. **Circuit Breaker**: Prevents cascading failures when services are down
2. **Retry with Backoff**: Handles transient failures gracefully
3. **Timeouts**: Prevents hanging requests from consuming resources
4. **Fallback**: Graceful degradation when services fail
5. **Dead-Letter Queues**: Handles failed messages for later replay

### Scalability
1. **Horizontal Scaling**: Stateless services scale across instances
2. **Read Replicas**: Database reads scale independently
3. **Caching**: Reduces database load
4. **Event Streaming**: Decouples producers from consumers

### Security
1. **JWT Authentication**: Stateless, scalable authentication
2. **Role-Based Authorization**: Fine-grained access control
3. **Password Hashing**: BCrypt prevents rainbow table attacks
4. **Secrets Management**: Environment variables, not hardcoded
5. **API Gateway Authentication**: Single point of control

### Data Consistency
1. **Eventual Consistency**: Trade immediate consistency for availability
2. **Idempotent Operations**: Handles message replay safely
3. **Optimistic Locking**: Prevents lost updates
4. **Saga Pattern**: Distributed transactions without 2PC
5. **Event Sourcing**: Complete audit trail

### Monitoring & Observability
1. **Structured Logging**: Searchable logs with correlation IDs
2. **Health Checks**: Automatic service discovery and failover
3. **Metrics**: Performance monitoring and alerting
4. **Distributed Tracing**: Request flow across services
5. **Central Logging**: Aggregated logs from all services

### Testing Strategy
1. **Unit Tests**: Fast, isolated, mock dependencies
2. **Integration Tests**: Verify component interactions
3. **Contract Tests**: Ensure service compatibility
4. **End-to-End Tests**: Complete workflow validation
5. **Performance Tests**: Load and stress testing

### DevOps & CI/CD
1. **Infrastructure as Code**: Reproducible infrastructure
2. **Automated Testing**: Quality gates in pipeline
3. **Containerization**: Consistent deployment across environments
4. **Orchestration**: Kubernetes for production deployment
5. **Blue-Green Deployment**: Zero-downtime updates

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## 📄 License

MIT License - See [LICENSE](LICENSE) file for details.

## 🎓 AI-Assisted Development

This project was developed with AI assistance tools for:
- Code generation and scaffolding
- Test generation (unit and integration)
- Documentation creation
- Code review and optimization
- Debugging and troubleshooting

However, all architectural decisions, design patterns, and business logic were carefully reviewed and validated to ensure production-grade quality. The AI tools served as development accelerators while maintaining code ownership and responsibility.

---

**Last Updated:** January 2024
**Java Version:** 21
**Spring Boot Version:** 3.2.0
**Status:** Production-Ready

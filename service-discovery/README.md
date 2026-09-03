# Service Discovery - Eureka Server

Spring Cloud Eureka server for service registration and discovery.

## Features
- Service registration and discovery
- Health checking
- Load balancing support
- Dashboard monitoring

## Configuration

```yaml
server:
  port: 8761

eureka:
  server:
    enable-self-preservation: false
  client:
    register-with-eureka: false
    fetch-registry: false
```

## Running

```bash
cd service-discovery
mvn spring-boot:run
```

Access dashboard: http://localhost:8761

# ---- build stage -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only the POMs first so dependency resolution is cached across code changes.
COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
RUN mvn -B -q -pl common,auth-service -am dependency:go-offline

COPY common/src common/src
COPY auth-service/src auth-service/src
RUN mvn -B -q -pl common,auth-service -am clean package -DskipTests

# ---- runtime stage -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is needed by the container HEALTHCHECK; the JRE image does not ship it.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /build/auth-service/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

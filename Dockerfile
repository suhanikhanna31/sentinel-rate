# ── Build stage ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

COPY rate-limiter-service/rate-limiter/mvnw .
COPY rate-limiter-service/rate-limiter/.mvn .mvn
COPY rate-limiter-service/rate-limiter/pom.xml .
COPY rate-limiter-service/rate-limiter/src src

# Download dependencies first (cached layer)
RUN ./mvnw dependency:go-offline -B -q

# Package, skip tests (tests run in CI before Docker build)
RUN ./mvnw package -B -q -DskipTests

# ── Runtime stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for least-privilege execution
RUN addgroup -S sentinel && adduser -S sentinel -G sentinel
USER sentinel

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

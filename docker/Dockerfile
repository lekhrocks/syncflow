# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY . ./

RUN ./gradlew :syncflow-api:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S syncflow && adduser -S syncflow -G syncflow

WORKDIR /app
COPY --from=builder /build/syncflow-api/build/libs/*.jar app.jar

RUN chown -R syncflow:syncflow /app

USER syncflow
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "--enable-preview", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

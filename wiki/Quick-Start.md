# Quick Start

## Prerequisites

- Java 25 (JDK with `--enable-preview` support)
- Docker and Docker Compose
- PostgreSQL 16+ (or use the Docker Compose service)

## 1. Clone and Build

```bash
git clone https://github.com/lekhrocks/syncflow.git
cd syncflow
./gradlew clean build
```

## 2. Start Infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d postgres
```

This starts PostgreSQL on port 5432 with the `syncflow` database.

## 3. Run the Application

```bash
./gradlew :syncflow-api:bootRun
```

The API starts on port 8080. Default admin credentials:

- **Username:** `admin`
- **Password:** `admin-test-password`

## 4. Verify

```bash
# Health check
curl http://localhost:8080/api/health

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin-test-password"}'
```

## 5. Swagger UI

Open http://localhost:8080/swagger-ui.html for interactive API exploration.

---

## Full Stack (Optional)

Start all services including MySQL, MongoDB, Kafka, and monitoring:

```bash
docker compose -f docker/docker-compose.yml up --build
```

Services:

| Service | Port | Profile |
|---------|------|---------|
| PostgreSQL | 5432 | always |
| MySQL | 3306 | mysql |
| MongoDB | 27017 | mongodb |
| Redis | 6379 | redis |
| Kafka | 9092 | kafka |
| Prometheus | 9090 | monitoring |
| Grafana | 3000 | monitoring |

---

## Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew verify` | Format check + compile + unit tests |
| `./gradlew integrationTest` | Testcontainers integration tests (needs Docker) |
| `./gradlew smokeTest` | Docker up, migrate, integration tests, tear down |
| `./gradlew e2eTest` | Full end-to-end with all services |
| `./gradlew benchmark` | JMH microbenchmarks |

# Contributing to SyncFlow

## Prerequisites
- Java 25 (Temurin recommended)
- Docker Desktop (for integration tests)
- Node.js 20+ (for UI development)

## Quick Start (5 minutes)

```bash
# 1. Build + run all tests
./gradlew verify

# 2. Start PostgreSQL
docker compose -f docker/docker-compose.yml up -d postgres

# 3. Start the API
./gradlew :syncflow-api:bootRun --args='--spring.profiles.active=local'

# 4. Start the UI (separate terminal)
cd syncflow-ui && npm install && npm run dev
```

## One-Command Developer Tasks

All tasks can be run from the project root:

```bash
make verify        # Format check + compile + all unit tests (fastest feedback)
make benchmark     # JMH microbenchmarks
make integration-test  # Testcontainers tests (requires Docker)
make smoke-test    # Spin up Docker, run smoke tests, tear down
make e2e-test      # Full end-to-end: spin up, all tests, tear down
make chaos-test    # LitmusChaos experiments (requires K8s)
make dev           # Start local environment
make dev-down      # Stop local environment
make clean         # Clean everything
```

Or use Gradle directly:

```bash
./gradlew verify
./gradlew benchmark
./gradlew integrationTest
./gradlew smokeTest
./gradlew e2eTest
./gradlew chaosTest
```

## Project Structure

```
syncflow/
├── syncflow-api/          # REST API, GraphQL, controllers, JPA entities
├── syncflow-core/         # Domain model, SPI interfaces, validation
├── syncflow-common/       # Shared utilities, exceptions, correlation IDs
├── syncflow-connectors/   # Database connector implementations (JDBC, MongoDB, Redis)
├── syncflow-plugin-api/   # Plugin SDK for third-party connectors
├── syncflow-agent/        # Data plane agent for customer VPCs
├── syncflow-security/     # Security configuration (RBAC utilities)
├── syncflow-monitoring/   # Metrics and observability utilities
├── syncflow-orchestrator/ # Workflow orchestration (future)
├── syncflow-test/         # Integration test suite
├── syncflow-ui/           # React 19 + Mantine 7 admin portal
├── docker/                # Docker Compose + Dockerfiles
├── helm/                  # Helm charts
├── k8s/                   # Kubernetes manifests
├── k6/                    # Load testing scripts
└── docs/                  # Documentation, ADRs, runbooks, diagrams
```

## Coding Standards

- **Java:** Palantir Java Format via Spotless (`./gradlew spotlessApply`)
- **TypeScript:** ESLint + Prettier (`cd syncflow-ui && npm run lint`)
- **Architecture:** Modules must not violate dependency rules (validated by ArchUnit tests)
- **Tests:** Unit tests in `src/test/java`, integration tests with `@Tag("integration")`

## Pull Request Checklist

- [ ] `./gradlew spotlessCheck` passes
- [ ] `./gradlew verify` passes (all unit tests)
- [ ] Integration tests pass with `./gradlew test -Dtests.integration=true`
- [ ] New endpoints have REST contract tests
- [ ] New domain objects have unit tests (boundary values, nulls, edge cases)
- [ ] CHANGELOG.md updated with `### Added` or `### Fixed` entry
- [ ] Migration strategy documented if database schema changes

## Need Help?
- Open a GitHub Discussion
- Join Slack: `#syncflow-dev`
- Read ADRs in `docs/adr/`

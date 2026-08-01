# SyncFlow Developer Makefile
# One-command local environment

.PHONY: help dev verify benchmark integration-test smoke-test e2e-test chaos-test clean

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

dev: ## Start full local environment (PostgreSQL + API + UI)
	docker compose -f docker/docker-compose.yml up -d postgres
	@echo "PostgreSQL ready on port 5432"
	@echo "Start API:   ./gradlew :syncflow-api:bootRun --args='--spring.profiles.active=local'"
	@echo "Start UI:    cd syncflow-ui && npm run dev"

dev-down: ## Stop local environment
	docker compose -f docker/docker-compose.yml down

verify: ## Full verification (format + compile + all tests)
	./gradlew verify

benchmark: ## Run JMH microbenchmarks
	./gradlew benchmark

integration-test: ## Run Testcontainers integration tests (requires Docker)
	./gradlew test -Dtests.integration=true

smoke-test: ## Spin up, migrate, smoke test, tear down
	./gradlew smokeTest

e2e-test: ## Full end-to-end: spin up, all tests, tear down
	./gradlew e2eTest

chaos-test: ## Run chaos experiments (requires K8s + LitmusChaos)
	kubectl apply -f scripts/chaos/experiment.yaml
	@echo "Monitoring chaos experiment..."
	kubectl get chaosengine -n syncflow -w

clean: ## Clean build artifacts
	./gradlew clean
	docker compose -f docker/docker-compose.yml down -v
	rm -rf syncflow-ui/dist syncflow-ui/node_modules

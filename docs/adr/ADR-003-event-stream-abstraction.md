# ADR-003: Why Event Publisher Abstraction over Direct Kafka?

## Status: Accepted

## Context
The CDC engine produces change events that must be consumed by the synchronization engine. Common options: embed Kafka, use an in-memory event bus, or design an abstract publisher interface.

## Decision
Abstract `EventPublisher` interface with `InMemoryEventPublisher` as the default implementation. Kafka, RabbitMQ, Pulsar, or SQS implementations can be added without changing the CDC or sync engines.

## Rationale
- **Development speed**: In-memory publisher works immediately — no Kafka cluster needed for development or testing.
- **Testability**: 11 CDC integration tests use `InMemoryEventPublisher` — no Kafka dependency in tests.
- **Operational simplicity**: Single-binary deployment for small teams; Kafka extraction for enterprise scale.
- **Contract stability**: `EventPublisher.publish(CDCEvent)` is stable — changing the transport doesn't change the event model.

## Consequences
- Kafka adapter is a single class implementing `EventPublisher` — estimated 50 lines.
- The `CDCEvent` record is the interchange format — must remain backward compatible.
- In-memory publisher has no persistence — events are lost on restart. This is acceptable because CDC offset tracking ensures replay.

## Links
- `EventPublisher.java`, `InMemoryEventPublisher.java`
- `CdcIntegrationTest.java` — validates the publish path end-to-end

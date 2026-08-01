# ADR-006: Why pgvector?

## Status: Deferred (Architecture Ready)

## Context
The AI Copilot uses RAG (Retrieval Augmented Generation) to answer questions about pipeline configurations, connector capabilities, and operational runbooks. The current keyword-based `KnowledgeBase.search()` is adequate for exact matches but cannot handle semantic queries ("databases with CDC support" vs "which DBs support change capture?").

## Decision
Prepare the architecture for pgvector-based vector search. The `KnowledgeBase.Document` model is ready for embedding indexing. Migration to pgvector is a data-loading exercise — no code changes to the AI agents.

## Rationale
- **Same database**: pgvector is a PostgreSQL extension — no new infrastructure.
- **Semantic search**: Vector embeddings understand meaning, not just keywords.
- **Incremental adoption**: Current `KnowledgeBase.search()` continues working; vector search is an alternative index.
- **SQL integration**: Vector similarity search uses standard SQL — `SELECT * FROM documents ORDER BY embedding <-> ? LIMIT 5`.

## Deferred Until
- `KnowledgeBase` document count exceeds 1000 entries.
- Users report that keyword search misses relevant results.
- AI Copilot evaluation metrics (hit rate, precision) fall below 80%.

## Consequences
- All documents are already stored with `Document(String content, String source)` — an `embedding` column can be added via Flyway migration.
- No new dependencies: pgvector is available as a PostgreSQL extension on all major cloud providers (RDS, Cloud SQL, Azure Database).
- Embedding generation can use the same LLM endpoint (`SYNCFLOW_AI_API_KEY`) via a simple batch job.

## Links
- `KnowledgeBase.java` — current keyword-indexed implementation

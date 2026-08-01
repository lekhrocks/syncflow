package com.syncflow.api.agent.ai.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class KnowledgeBase {

    private final List<Document> documents = new ArrayList<>();

    public KnowledgeBase() {
        seed();
    }

    public List<Document> search(String query) {
        var terms = query.toLowerCase().split("\\s+");
        return documents.stream()
                .filter(d -> Arrays.stream(terms).anyMatch(t -> d.content().toLowerCase().contains(t)))
                .limit(5)
                .toList();
    }

    public void index(Document doc) {
        documents.add(doc);
    }

    private void seed() {
        documents.add(new Document("Connectors support PostgreSQL, MySQL, MongoDB, and Redis databases. " +
                "PostgreSQL uses Debezium for CDC. MySQL uses Debezium. MongoDB uses Change Streams.",
                "Connector Documentation"));
        documents.add(new Document("Pipelines are designed using the Pipeline Designer. " +
                "Each pipeline has a source, destination, table mappings, transformations, and filters.",
                "Pipeline Documentation"));
        documents.add(new Document("The Snapshot Engine performs one-time bulk synchronization. " +
                "It reads all rows, applies mappings and transformations, and writes to the destination.",
                "Snapshot Engine Documentation"));
        documents.add(new Document("The CDC Engine captures INSERT, UPDATE, DELETE operations. " +
                "PostgreSQL supports pgoutput plugin. MySQL supports GTID and binlog. MongoDB supports Change Streams.",
                "CDC Engine Documentation"));
        documents.add(new Document(
                "The Synchronization Engine processes CDC events and applies them to destinations. " +
                        "Supports at-least-once delivery with idempotency and dead letter queues.",
                "Synchronization Engine Documentation"));
    }

    public record Document(String content, String source) {
    }
}

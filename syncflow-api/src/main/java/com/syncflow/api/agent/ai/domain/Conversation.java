package com.syncflow.api.agent.ai.domain;

import java.time.Instant;
import java.util.List;

public record Conversation(
        String conversationId,
        String userId,
        String tenantId,
        List<Message> messages,
        Instant createdAt,
        Instant updatedAt) {

    public record Message(
            String role,
            String content,
            Instant timestamp,
            String agentType) {
    }
}

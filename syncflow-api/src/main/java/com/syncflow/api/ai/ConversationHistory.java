package com.syncflow.api.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationHistory {

    private final Map<String, List<Entry>> sessions = new ConcurrentHashMap<>();
    private final int maxEntries;

    public ConversationHistory(AiProperties props) {
        this.maxEntries = props.getMaxHistory();
    }

    public void add(String sessionId, String role, String content) {
        sessions.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new Entry(role, content, System.currentTimeMillis()));
        var list = sessions.get(sessionId);
        while (list.size() > maxEntries)
            list.removeFirst();
    }

    public List<Entry> history(String sessionId) {
        var list = sessions.get(sessionId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public record Entry(String role, String content, long timestamp) {
    }
}

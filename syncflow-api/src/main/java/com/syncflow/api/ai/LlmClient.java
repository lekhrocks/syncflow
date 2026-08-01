package com.syncflow.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {

    private final AiProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public LlmClient(AiProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    public String call(String prompt, String sessionId) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "model", props.getModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", props.getMaxTokens(),
                    "temperature", props.getTemperature()));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMinutes(2))
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var json = mapper.readTree(response.body());
            return json.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            return "AI service unavailable: " + e.getMessage();
        }
    }
}

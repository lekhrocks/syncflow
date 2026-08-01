package com.syncflow.api.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "syncflow.ai")
public class AiProperties {

    private String endpoint = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-4o";
    private String apiKey = "";
    private int maxTokens = 4096;
    private double temperature = 0.3;
    private int maxHistory = 20;

    public String getEndpoint() {
        return endpoint;
    }
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public String getApiKey() {
        return apiKey;
    }
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    public int getMaxTokens() {
        return maxTokens;
    }
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    public double getTemperature() {
        return temperature;
    }
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    public int getMaxHistory() {
        return maxHistory;
    }
    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }
}

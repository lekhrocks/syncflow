package com.syncflow.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "syncflow")
public class SyncFlowProperties {

    private Pipeline pipeline = new Pipeline();
    private Connector connector = new Connector();

    public Pipeline getPipeline() {
        return pipeline;
    }
    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public Connector getConnector() {
        return connector;
    }
    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public static class Pipeline {

        private int maxNameLength = 255;
        private String defaultStatus = "CREATED";

        public int getMaxNameLength() {
            return maxNameLength;
        }
        public void setMaxNameLength(int maxNameLength) {
            this.maxNameLength = maxNameLength;
        }
        public String getDefaultStatus() {
            return defaultStatus;
        }
        public void setDefaultStatus(String defaultStatus) {
            this.defaultStatus = defaultStatus;
        }
    }

    public static class Connector {

        private Duration connectionTimeout = Duration.ofSeconds(30);
        private int retryCount = 3;

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }
        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
        public int getRetryCount() {
            return retryCount;
        }
        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
    }
}

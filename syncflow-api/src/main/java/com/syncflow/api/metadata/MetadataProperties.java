package com.syncflow.api.metadata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "syncflow.metadata")
public class MetadataProperties {

    private Duration cacheTtl = Duration.ofMinutes(5);
    private int sampleSize = 100;

    public Duration getTtl() {
        return cacheTtl;
    }
    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getSampleSize() {
        return sampleSize;
    }
    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }
}

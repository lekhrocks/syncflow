package com.syncflow.api;

import com.syncflow.api.config.RuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.syncflow")
@EnableScheduling
@EnableConfigurationProperties(RuntimeProperties.class)
public class SyncFlowApplication {

    static void main(String[] args) {
        SpringApplication.run(SyncFlowApplication.class, args);
    }
}

package com.syncflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.syncflow")
@EnableScheduling
public class SyncFlowApplication {

    static void main(String[] args) {
        SpringApplication.run(SyncFlowApplication.class, args);
    }
}

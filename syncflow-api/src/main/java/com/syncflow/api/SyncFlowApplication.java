package com.syncflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.syncflow")
public class SyncFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncFlowApplication.class, args);
    }
}

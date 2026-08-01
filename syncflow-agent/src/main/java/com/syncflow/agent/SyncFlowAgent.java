package com.syncflow.agent;

import com.syncflow.agent.client.AgentRegistrar;
import com.syncflow.agent.client.HeartbeatSender;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication(scanBasePackages = "com.syncflow.agent")
public class SyncFlowAgent {

    private final AgentRegistrar registrar;
    private final HeartbeatSender heartbeat;

    public SyncFlowAgent(AgentRegistrar registrar, HeartbeatSender heartbeat) {
        this.registrar = registrar;
        this.heartbeat = heartbeat;
    }

    public static void main(String[] args) {
        SpringApplication.run(SyncFlowAgent.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        registrar.register();
        heartbeat.start();
    }
}

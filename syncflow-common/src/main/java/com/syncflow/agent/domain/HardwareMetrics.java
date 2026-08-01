package com.syncflow.agent.domain;

public record HardwareMetrics(
        double cpuPercent,
        long memoryUsed,
        long memoryTotal,
        long diskUsed,
        long diskTotal,
        int runningJobs,
        long networkRx,
        long networkTx) {

    public static HardwareMetrics empty() {
        return new HardwareMetrics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}

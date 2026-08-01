package com.syncflow.core.spi;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CaptureStatus;

import java.util.function.Consumer;

public interface CdcCapableConnector extends SnapshotCapableConnector {

    void startCDC(ConnectorContext context, Consumer<CDCEvent> eventConsumer);

    void stopCDC();

    void pauseCDC();

    void resumeCDC();

    boolean isCdcActive();

    CaptureStatus captureStatus();

    java.util.Map<String, String> currentOffset();

    default boolean supportsCdc() {
        return true;
    }
}

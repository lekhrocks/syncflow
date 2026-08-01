package com.syncflow.api.sync;

import com.syncflow.core.cdc.CDCEvent;
import java.util.List;

/**
 * Stub DestinationRouter for sync engine unit tests.
 * Simulates write behavior without external dependencies.
 */
class DestinationRouterStub {

    private boolean shouldFail = false;
    private String failMessage = null;
    private int writeCount = 0;

    void setFailNext(String message) {
        this.shouldFail = true;
        this.failMessage = message;
    }

    DestinationRouter.WriteResult write(String connectionId, CDCEvent event, List<String> destColumns) {
        writeCount++;
        if (shouldFail) {
            shouldFail = false;
            return new DestinationRouter.WriteResult(false, failMessage);
        }
        return new DestinationRouter.WriteResult(true, null);
    }

    int getWriteCount() {
        return writeCount;
    }
    void reset() {
        writeCount = 0;
        shouldFail = false;
        failMessage = null;
    }
}

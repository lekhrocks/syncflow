package com.syncflow.api.plugin.adapter;

/**
 * The plugin SPI's {@code readBatch} takes a {@code batchNumber}, not a
 * cursor string. We use this codec to round-trip a batch number through
 * the core {@code BatchInformation.cursor} field so the existing snapshot
 * pipeline doesn't have to know the difference.
 *
 * <p>
 * Format: {@code "b:<n>"}. A {@code null} cursor maps to batch 0.
 */
public final class PluginCursorCodec {

    private static final String PREFIX = "b:";

    private PluginCursorCodec() {
    }

    public static String encode(int batchNumber) {
        return PREFIX + batchNumber;
    }

    public static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        if (!cursor.startsWith(PREFIX)) {
            // Unknown format — start from zero rather than fail the snapshot.
            return 0;
        }
        try {
            return Integer.parseInt(cursor.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

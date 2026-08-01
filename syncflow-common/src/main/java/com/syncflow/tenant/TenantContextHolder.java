package com.syncflow.tenant;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext ctx) {
        CURRENT.set(ctx);
    }

    public static TenantContext get() {
        return CURRENT.get();
    }

    public static TenantId getTenantId() {
        var ctx = CURRENT.get();
        return ctx == null ? TenantId.DEFAULT : ctx.tenantId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

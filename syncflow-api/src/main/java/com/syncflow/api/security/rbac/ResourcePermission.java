package com.syncflow.api.security.rbac;

import java.util.EnumSet;

public enum ResourcePermission {

    PIPELINE_READ, PIPELINE_WRITE, PIPELINE_DELETE, PIPELINE_EXECUTE, CONNECTION_READ, CONNECTION_WRITE, CONNECTION_DELETE, EXECUTION_READ, EXECUTION_CANCEL, EXECUTION_RESTART, ORG_READ, ORG_WRITE, WORKSPACE_READ, WORKSPACE_WRITE, PROJECT_READ, PROJECT_WRITE, APIKEY_READ, APIKEY_ISSUE, APIKEY_REVOKE, AUDIT_READ, AI_USE, METRICS_READ;

    public static EnumSet<ResourcePermission> viewer() {
        return EnumSet.of(PIPELINE_READ, CONNECTION_READ, EXECUTION_READ, METRICS_READ);
    }

    public static EnumSet<ResourcePermission> developer() {
        EnumSet<ResourcePermission> s = viewer();
        s.add(PIPELINE_WRITE);
        s.add(PIPELINE_EXECUTE);
        s.add(CONNECTION_WRITE);
        s.add(AI_USE);
        return s;
    }

    public static EnumSet<ResourcePermission> operator() {
        EnumSet<ResourcePermission> s = viewer();
        s.add(EXECUTION_CANCEL);
        s.add(EXECUTION_RESTART);
        s.add(PIPELINE_EXECUTE);
        return s;
    }

    public static EnumSet<ResourcePermission> auditor() {
        EnumSet<ResourcePermission> s = EnumSet.noneOf(ResourcePermission.class);
        s.add(AUDIT_READ);
        s.add(METRICS_READ);
        return s;
    }

    public static EnumSet<ResourcePermission> workspaceAdmin() {
        EnumSet<ResourcePermission> s = EnumSet.noneOf(ResourcePermission.class);
        s.addAll(java.util.Arrays.asList(values()));
        return s;
    }
}

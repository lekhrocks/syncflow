package com.syncflow.api.user;

import java.util.Set;

/** Known role names understood by the platform. */
public final class RoleConstants {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    public static final Set<String> ALLOWED = Set.of(ADMIN, USER);

    private RoleConstants() {
    }

    /** True if every role is in the known set. */
    public static boolean allKnown(String csv) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .allMatch(ALLOWED::contains);
    }
}

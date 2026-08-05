package com.syncflow.core.connection;

public record Credentials(String username, String password) {

    public Credentials {
        if (username == null) {
            username = "";
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        // username is required whenever a password is supplied (authenticated
        // services).
        // For unauthenticated services (MongoDB/Redis no-auth) both are empty strings —
        // allowed.
        if (username.isBlank() && !password.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    @Override
    public String toString() {
        return "Credentials{username='" + username + "', password='******'}";
    }
}

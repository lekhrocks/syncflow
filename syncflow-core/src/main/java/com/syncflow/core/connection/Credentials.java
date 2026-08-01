package com.syncflow.core.connection;

public record Credentials(String username, String password) {

    public Credentials {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
    }

    @Override
    public String toString() {
        return "Credentials{username='" + username + "', password='******'}";
    }
}

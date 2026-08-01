package com.syncflow.plugin.config;

import java.util.List;
import java.util.Map;

public record ConfigurationSchema(
        List<ConfigProperty> properties,
        List<String> required,
        Map<String, Object> defaults) {

    public static ConfigurationSchema empty() {
        return new ConfigurationSchema(List.of(), List.of(), Map.of());
    }

    public record ConfigProperty(
            String name,
            String label,
            String type,
            String description,
            String defaultValue,
            boolean required,
            boolean sensitive,
            String validationPattern) {

        public static ConfigProperty host() {
            return new ConfigProperty("host", "Host", "string",
                    "Database hostname or IP", "localhost", true, false, null);
        }

        public static ConfigProperty port() {
            return new ConfigProperty("port", "Port", "integer",
                    "Database port", null, true, false, "^\\d+$");
        }

        public static ConfigProperty database() {
            return new ConfigProperty("database", "Database", "string",
                    "Database name", null, true, false, null);
        }

        public static ConfigProperty username() {
            return new ConfigProperty("username", "Username", "string",
                    "Authentication username", null, true, false, null);
        }

        public static ConfigProperty password() {
            return new ConfigProperty("password", "Password", "password",
                    "Authentication password", null, true, true, null);
        }
    }
}

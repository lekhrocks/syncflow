package com.syncflow.core.connection.spi;

import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.DetailedValidationResult;

public interface ConnectionValidator {

    DetailedValidationResult validate(ConnectionProperties properties, Credentials credentials);

    boolean supports(com.syncflow.core.connection.ConnectionType type);
}

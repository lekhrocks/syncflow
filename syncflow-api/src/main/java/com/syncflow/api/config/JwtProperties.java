package com.syncflow.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing properties bound from {@code syncflow.jwt.*}.
 * Pure data holder — bean wiring lives in {@link JwtSecurityConfig} so the
 * properties binding is not entangled with bean lifecycle.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "syncflow.jwt")
public class JwtProperties {

    /** Base64-encoded HMAC secret (HS256 requires >= 256-bit key = 32 bytes). */
    private String secret;

    /** JWT issuer claim. */
    private String issuer = "syncflow";

    /** Token lifetime in minutes. */
    private long expiryMinutes = 60;
}

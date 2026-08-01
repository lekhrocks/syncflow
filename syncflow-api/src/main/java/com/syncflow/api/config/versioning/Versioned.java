package com.syncflow.api.config.versioning;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Versioned {

    ApiVersion value() default ApiVersion.V1;

    ApiVersion deprecatedSince() default ApiVersion.V1;

    ApiVersion removedIn() default ApiVersion.V2;
}

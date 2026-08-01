package com.syncflow.api.config.versioning;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class VersionContext {

    private ApiVersion version = ApiVersion.V1;

    public ApiVersion getVersion() {
        return version;
    }
    public void setVersion(ApiVersion version) {
        this.version = version;
    }

    public boolean isV1() {
        return version == ApiVersion.V1;
    }
    public boolean isV2() {
        return version == ApiVersion.V2;
    }
}

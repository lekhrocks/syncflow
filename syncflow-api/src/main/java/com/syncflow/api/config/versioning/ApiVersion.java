package com.syncflow.api.config.versioning;

public enum ApiVersion {

    V1("2026-01-01", null), V2("2026-07-01", "2026-10-01");

    private final String releaseDate;
    private final String deprecationDate;

    ApiVersion(String releaseDate, String deprecationDate) {
        this.releaseDate = releaseDate;
        this.deprecationDate = deprecationDate;
    }

    public String releaseDate() {
        return releaseDate;
    }
    public String deprecationDate() {
        return deprecationDate;
    }
    public boolean isDeprecated() {
        return deprecationDate != null;
    }
}

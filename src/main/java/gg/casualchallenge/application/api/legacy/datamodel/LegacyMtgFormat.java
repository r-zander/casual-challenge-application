package gg.casualchallenge.application.api.legacy.datamodel;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum LegacyMtgFormat {
    @JsonProperty("Standard")
    STANDARD,
    @JsonProperty("Pioneer")
    PIONEER,
    @JsonProperty("Pauper")
    PAUPER,
    @JsonProperty("Modern")
    MODERN,
    @JsonProperty("Legacy")
    LEGACY,
    @JsonProperty("Vintage")
    VINTAGE,
}

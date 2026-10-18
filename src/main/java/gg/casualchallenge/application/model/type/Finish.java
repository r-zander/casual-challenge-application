package gg.casualchallenge.application.model.type;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Finish {
    @JsonProperty("nonfoil")
    NONFOIL,
    @JsonProperty("foil")
    FOIL,
}

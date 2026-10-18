package gg.casualchallenge.application.model.type;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PriceProvider {
    @JsonProperty("cardmarket")
    CARDMARKET,
    @JsonProperty("tcgplayer")
    TCGPLAYER,
}

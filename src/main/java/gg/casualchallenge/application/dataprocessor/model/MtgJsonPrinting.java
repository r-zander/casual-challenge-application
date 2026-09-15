package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.util.UUID;

@Value
public class MtgJsonPrinting {
    UUID uuid;
    String cardName;
    boolean foil;
    boolean nonFoil;
}

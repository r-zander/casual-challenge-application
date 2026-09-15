package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

@Value
public class MtgJsonPrinting {
    String uuid;
    String cardName;
    boolean foil;
    boolean nonFoil;
}

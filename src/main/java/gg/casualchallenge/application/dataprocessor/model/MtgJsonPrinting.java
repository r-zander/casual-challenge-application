package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.type.Finish;
import lombok.Value;

import java.util.Set;
import java.util.UUID;

@Value
public class MtgJsonPrinting {
    UUID uuid;
    String cardName;
    String setCode;
    String number;
    Set<Finish> finishes;
}

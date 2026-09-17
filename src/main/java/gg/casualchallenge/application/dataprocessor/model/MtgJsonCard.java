package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
public class MtgJsonCard {
    String name;
    UUID oracleId;
    boolean vintageLegal;
    boolean vintageRestricted;
    MtgFormat bannedIn;
    String firstSetCode;
    LocalDate firstReleaseDate;
}

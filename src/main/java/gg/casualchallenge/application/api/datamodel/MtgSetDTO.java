package gg.casualchallenge.application.api.datamodel;

import gg.casualchallenge.application.model.type.MtgSetType;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class MtgSetDTO {
    String name;
    String code;
    LocalDate releaseDate;
    MtgSetType type;
    List<String> commanderDecks;
    List<String> childCodes;
    int newCardCount;
}

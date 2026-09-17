package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.MtgSetType;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class MtgSetVO {
    String name;
    String code;
    LocalDate releaseDate;
    MtgSetType type;
    List<String> commanderDecks;
    List<String> childCodes; // commander set, promos etc. whose new cards are counted in here
    int newCardCount;
}

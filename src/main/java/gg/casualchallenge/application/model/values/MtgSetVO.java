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
    /** Commander set, promos etc. whose new cards are counted in here */
    List<ChildSetVO> childSets;
    int newCardCount;

    @Value
    public static class ChildSetVO {
        String name;
        String code;
        int newCardCount;
    }

}

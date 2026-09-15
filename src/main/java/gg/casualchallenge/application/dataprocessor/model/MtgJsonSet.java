package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class MtgJsonSet {
    String name;
    String code;
    LocalDate releaseDate;
    String type;
    String parentCode;
    boolean onlineOnly;
    List<MtgJsonDeck> decks;

    @Value
    public static class MtgJsonDeck {
        String name;
        String type;
        LocalDate releaseDate;
    }

}

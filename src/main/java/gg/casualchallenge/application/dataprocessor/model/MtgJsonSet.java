package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.type.MtgSetType;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class MtgJsonSet {
    String name;
    String code;
    LocalDate releaseDate;
    MtgSetType setType;
    String parentCode;
    boolean onlineOnly;
    List<MtgJsonDeck> decks;

    @Value
    public static class MtgJsonDeck {
        String name;
        String deckType; // free text, "Commander Deck" and "MTGO Commander Deck" are both a thing
        LocalDate releaseDate;
    }

}

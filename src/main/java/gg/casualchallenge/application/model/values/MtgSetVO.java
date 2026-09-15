package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class MtgSetVO {
    String name;
    String code;
    LocalDate releaseDate;
    String type;
    List<String> commanderDecks;
}

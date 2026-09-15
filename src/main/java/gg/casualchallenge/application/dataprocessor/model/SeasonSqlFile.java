package gg.casualchallenge.application.dataprocessor.model;

import lombok.Getter;

@Getter
public enum SeasonSqlFile {
    ADD_SEASON("00_add_season", "_"),
    INSERT_CARDS("01_insert_cards", ""), // the card table belongs to no season
    INSERT_CARD_SEASON_DATA("02_insert_card_season_data", "_for_season_"),
    ;

    private final String part;
    private final String seasonSuffix;

    SeasonSqlFile(String part, String seasonSuffix) {
        this.part = part;
        this.seasonSuffix = seasonSuffix;
    }
}

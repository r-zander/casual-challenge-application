package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

@Value
public class CommittedSeasonCountsDTO {
    int remappedCards;
    int updatedCardNames;
    int insertedCards;
    int upsertedCardSeasonData;
}

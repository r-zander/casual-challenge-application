package gg.casualchallenge.application.model.values;

import lombok.Value;

@Value
public class CommittedSeasonCountsVO {
    int remappedCards;
    int renamedCards;
    int insertedCards;
    int upsertedCardSeasonData;
}

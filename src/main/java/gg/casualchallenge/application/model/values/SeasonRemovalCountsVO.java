package gg.casualchallenge.application.model.values;

import lombok.Value;

@Value
public class SeasonRemovalCountsVO {
    int cardSeasonDataRows;
    int deletedCards;
    int undoneRemaps;
    int undoneRenames;
}

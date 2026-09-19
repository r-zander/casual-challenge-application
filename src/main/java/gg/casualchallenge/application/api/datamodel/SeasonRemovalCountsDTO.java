package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

@Value
public class SeasonRemovalCountsDTO {
    int cardSeasonDataRows;
    int deletedCards;
    int undoneRemaps;
    int undoneRenames;
}

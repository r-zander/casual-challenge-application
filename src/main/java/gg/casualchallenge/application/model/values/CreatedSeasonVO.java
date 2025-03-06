package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.util.List;

@Value
public class CreatedSeasonVO {
    SeasonVO season;
    List<CardVO> addedCards;
    List<MtgSetVO> addedSets;
}

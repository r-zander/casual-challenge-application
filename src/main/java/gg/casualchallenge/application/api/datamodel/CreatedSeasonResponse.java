package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class CreatedSeasonResponse {
    int seasonNumber;
    LocalDate startDate;
    LocalDate endDate;
    List<CardDTO> addedCards;
    List<MtgSetDTO> addedSets;

}

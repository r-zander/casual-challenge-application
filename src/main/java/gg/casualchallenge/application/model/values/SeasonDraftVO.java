package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
public class SeasonDraftVO {
    int id;
    int seasonNumber;
    LocalDate startDate;
    LocalDate endDate;
    LocalDate priceWindowStart;
    LocalDate priceWindowEnd;
    int previousSeasonId;
    LocalDateTime previousSeasonUpdatedAt;
    String mtgJsonDate;
    String metaSource;
    LocalDateTime preparedAt;
    String preparedBy;
    LocalDateTime committedAt;
    String committedBy;
    String report;
}

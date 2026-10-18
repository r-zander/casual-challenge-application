package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import lombok.Value;
import lombok.With;

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
    LocalDate previousSeasonEndDate;
    LocalDateTime previousSeasonUpdatedAt;
    LocalDate mtgJsonDate;
    MetaShareSource metaSource;
    LocalDateTime preparedAt;
    String preparedBy;
    /**
     * The migration files are named after the commit,
     * so they have to be built with the value the commit is about to write.
     */
    @With
    LocalDateTime committedAt;
    @With
    String committedBy;
    String migrationBranch;
    String pullRequestUrl;
    LocalDateTime removedAt;
    String removedBy;
    String report;
}

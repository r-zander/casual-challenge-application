package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.time.LocalDate;

@Value
public class RemovedSeasonVO {
    int seasonNumber;
    int previousSeasonNumber;
    LocalDate previousSeasonEndDate;
    String closedPullRequestUrl;
    boolean archiveDeleted;
    SeasonRemovalCountsVO counts;
}

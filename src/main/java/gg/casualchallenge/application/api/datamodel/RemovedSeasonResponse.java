package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.time.LocalDate;

@Value
public class RemovedSeasonResponse {
    int seasonNumber;
    int previousSeasonNumber;
    LocalDate previousSeasonEndDate;
    String closedPullRequestUrl;
    boolean archiveDeleted;
    SeasonRemovalCountsDTO counts;
}

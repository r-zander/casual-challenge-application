package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class CommittedSeasonResponse {
    int seasonNumber;
    String romanSeasonNumber;
    LocalDate startDate;
    LocalDate finalsFriday;
    LocalDate endDate;
    LocalDate nextSeasonStart;
    List<MtgSetDTO> newSets;
    SeasonDraftReportResponse.ScryfallDecksDTO scryfallDecks;
    CommittedSeasonCountsDTO counts;
    PullRequestDTO pullRequest;
}

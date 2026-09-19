package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class CommittedSeasonVO {
    int seasonNumber;
    String romanSeasonNumber;
    LocalDate startDate;
    LocalDate finalsFriday;
    LocalDate endDate;
    LocalDate nextSeasonStart;
    List<MtgSetVO> newSets;
    SeasonDraftReportVO.ScryfallDecksVO scryfallDecks;
    CommittedSeasonCountsVO counts;
    PullRequestVO pullRequest; // null when the migrations were not pushed to GitHub
}

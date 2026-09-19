package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value // same field names as SeasonRemovalPreviewVO
public class SeasonRemovalPreviewResponse {
    int seasonNumber;
    LocalDate startDate;
    LocalDate endDate;
    LocalDateTime committedAt;
    String committedBy;
    boolean removable;
    List<String> refusals;
    int cardSeasonDataRows;
    int cardsAddedBySeason;
    int oracleIdRemaps;
    int renamedCards;
    Integer previousSeasonNumber;
    LocalDate previousSeasonEndDate;
    String pullRequestUrl;
    String archiveDirectory;
}

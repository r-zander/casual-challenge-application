package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
public class SeasonRemovalPreviewVO { // what a removal would do, and everything that stands in its way
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

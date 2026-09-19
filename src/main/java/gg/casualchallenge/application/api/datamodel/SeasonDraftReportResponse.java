package gg.casualchallenge.application.api.datamodel;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class SeasonDraftReportResponse { // same field names as SeasonDraftReportVO, so this and the report in season_draft stay diffable
    int seasonNumber;
    String romanSeasonNumber;
    LocalDate startDate;
    LocalDate endDate;
    LocalDate finalsFriday;
    LocalDate nextSeasonStart;
    LocalDate priceWindowStart;
    LocalDate priceWindowEnd;
    LocalDate mtgJsonDate;
    String mtgJsonVersion;
    String metaSource;
    int previousSeasonNumber;
    LocalDateTime preparedAt;
    String preparedBy;
    LocalDateTime committedAt;
    String committedBy;
    String pullRequestUrl;
    CountsDTO counts;
    List<String> duplicateMetaShareNames;
    List<BanChangeDTO> newBans;
    List<BanChangeDTO> unbans;
    List<BanChangeDTO> newExtended;
    List<BanChangeDTO> noLongerExtended;
    List<BudgetPointChangeDTO> budgetPointChanges;
    List<BudgetPointChangeDTO> topIncreases;
    List<BudgetPointChangeDTO> topDecreases;
    List<BudgetPointChangeDTO> zeroBudgetPointCards;
    List<LeftOutCardDTO> missingCards;
    List<LeftOutCardDTO> skippedCards;
    List<OracleIdChangeDTO> oracleIdChanges;
    List<RenamedCardDTO> renamedCards;
    List<RenamedCardDTO> normalizedNameFixes;
    List<MtgSetDTO> setsReleased;
    ScryfallDecksDTO scryfallDecks;

    @Value
    public static class CountsDTO {
        int cards;
        int newCards;
        int cardsWithoutPrice;
        Map<Legality, Integer> legalities;
        double exchangeRate;
        double adjustedExchangeRate;
        int pricesFixedByExchangeRateCount;
        int pricedDays;
        Map<MtgFormat, Integer> top50Rows;
        Map<MtgFormat, Integer> top150Rows;
        int newBans;
        int unbans;
        int newExtended;
        int noLongerExtended;
        int budgetPointChanges;
        int missingCards;
        int skippedCards;
        int oracleIdChanges;
        int renamedCards;
        int normalizedNameFixes;
    }

    @Value
    public static class BanChangeDTO {
        String name;
        Integer budgetPoints;
        Map<MtgFormat, BigDecimal> metaShares;
        MtgFormat bannedIn;
        boolean vintageRestricted;
    }

    @Value
    public static class BudgetPointChangeDTO {
        String name;
        Integer previousBudgetPoints;
        Integer budgetPoints;
        int change;
    }

    @Value
    public static class LeftOutCardDTO {
        String name;
        UUID oracleId;
        String reason;
    }

    @Value
    public static class OracleIdChangeDTO {
        String name;
        UUID previousOracleId;
        UUID oracleId;
        String firstSetCode;
    }

    @Value
    public static class RenamedCardDTO {
        UUID oracleId;
        String previousName;
        String previousNormalizedName;
        String name;
        String normalizedName;
    }

    @Value
    public static class ScryfallDecksDTO {
        String newBans;
        String unbans;
        String currentBans;
    }

}

package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;
import lombok.With;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything you need to decide whether the draft is good enough to commit */
@Value
public class SeasonDraftReportVO {
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
    MetaShareSource metaSource;
    int previousSeasonNumber;
    LocalDateTime preparedAt;
    String preparedBy;
    // The report is stored before the commit, SeasonDraftService fills these two from season_draft
    @With
    LocalDateTime committedAt;
    @With
    String committedBy;
    /** Where the migrations went, null when they were downloaded by hand */
    @With
    String pullRequestUrl;
    CountsVO counts;
    List<String> duplicateMetaShareNames;
    List<BanChangeVO> newBans;
    List<BanChangeVO> unbans;
    List<BanChangeVO> newExtended;
    List<BanChangeVO> noLongerExtended;
    List<BudgetPointChangeVO> budgetPointChanges;
    List<BudgetPointChangeVO> topIncreases;
    List<BudgetPointChangeVO> topDecreases;
    List<BudgetPointChangeVO> zeroBudgetPointCards;
    List<LeftOutCardVO> missingCards;
    List<LeftOutCardVO> skippedCards;
    List<OracleIdChangeVO> oracleIdChanges;
    List<RenamedCardVO> renamedCards;
    List<RenamedCardVO> normalizedNameFixes;
    List<MtgSetVO> setsReleased;
    ScryfallDecksVO scryfallDecks;

    @Value
    public static class CountsVO {
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
    public static class BanChangeVO {
        String name;
        Integer budgetPoints;
        Map<MtgFormat, BigDecimal> metaShares;
        MtgFormat bannedIn;
        boolean vintageRestricted;
    }

    @Value
    public static class BudgetPointChangeVO {
        String name;
        Integer previousBudgetPoints;
        Integer budgetPoints;
        int change;
    }

    /** A card that did not make it into the season, with the reason why */
    @Value
    public static class LeftOutCardVO {
        String name;
        UUID oracleId;
        String reason;
    }

    @Value
    public static class OracleIdChangeVO {
        String name;
        UUID previousOracleId;
        UUID oracleId;
        String firstSetCode;
    }

    @Value
    public static class RenamedCardVO {
        UUID oracleId;
        String previousName;
        String previousNormalizedName;
        String name;
        String normalizedName;
    }

    /** The three lists that get pasted into Scryfall to build the season decks */
    @Value
    public static class ScryfallDecksVO {
        String newBans;
        String unbans;
        String currentBans;
    }

}

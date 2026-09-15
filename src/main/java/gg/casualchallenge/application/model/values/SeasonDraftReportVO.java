package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything a human needs to decide whether the draft is good enough to commit. */
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
    String metaSource;
    int previousSeasonNumber;
    LocalDateTime preparedAt;
    CountsVO counts;
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
        int normalizedNameFixes;
        int zeroBudgetPointCards;
        Map<Legality, Integer> legalities;
        double exchangeRate;
        double adjustedExchangeRate;
        int pricesFixedByExchangeRate;
        Map<MtgFormat, Integer> top50Rows;
        Map<MtgFormat, Integer> top150Rows;
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

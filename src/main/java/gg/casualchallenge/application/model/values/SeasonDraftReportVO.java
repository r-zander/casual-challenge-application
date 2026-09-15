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

@Value
public class SeasonDraftReportVO { // everything a human needs to decide whether the draft is good enough to commit
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
        int cardsWithoutPrice;
        Map<Legality, Integer> legalities;
        double exchangeRate;
        double adjustedExchangeRate;
        int pricesFixedByExchangeRate;
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

    @Value
    public static class LeftOutCardVO { // a card that did not make it into the season, with the reason why
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

    @Value
    public static class ScryfallDecksVO { // the three lists that get pasted into Scryfall to build the season decks
        String newBans;
        String unbans;
        String currentBans;
    }

}

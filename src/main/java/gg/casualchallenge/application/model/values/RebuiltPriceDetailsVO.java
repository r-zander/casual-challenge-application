package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.PriceProvider;
import lombok.Value;

import java.util.List;

@Value
public class RebuiltPriceDetailsVO {
    int seasonNumber;
    int storedCount;
    int mismatchCount;
    /** The first 20 */
    List<MismatchVO> mismatches;
    int unmatchedCount;
    /** The first 20 */
    List<UnmatchedCardVO> unmatchedCards;
    /** What the rebuilt documents convert the tcgplayer prices with */
    double adjustedExchangeRate;
    /** null = no committed draft to compare with */
    Double committedAdjustedExchangeRate;

    /** A card the archive prices differently today than the budget points the season has. */
    @Value
    public static class MismatchVO {
        String name;
        Integer storedBudgetPoints;
        int recalculatedBudgetPoints;
        /** null without a price */
        PriceProvider pricedBy;
    }

    /** A card with budget points in the season, but no card in the archive. */
    @Value
    public static class UnmatchedCardVO {
        String name;
        Integer storedBudgetPoints;
    }

}

package gg.casualchallenge.application.model.values;

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

    /** A card the archive prices differently today than the budget points the season has. */
    @Value
    public static class MismatchVO {
        String name;
        Integer storedBudgetPoints;
        int recalculatedBudgetPoints;
    }

    /** A card with budget points in the season, but no card in the archive. */
    @Value
    public static class UnmatchedCardVO {
        String name;
        Integer storedBudgetPoints;
    }

}

package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.util.List;

@Value
public class RebuiltPriceDetailsResponse {
    int seasonNumber;
    int storedCount;
    int mismatchCount;
    List<MismatchDTO> mismatches;
    int unmatchedCount;
    List<UnmatchedCardDTO> unmatchedCards;

    @Value
    public static class MismatchDTO {
        String name;
        Integer storedBudgetPoints;
        int recalculatedBudgetPoints;
    }

    @Value
    public static class UnmatchedCardDTO {
        String name;
        Integer storedBudgetPoints;
    }

}

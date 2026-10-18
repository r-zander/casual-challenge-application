package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.util.List;

@Value
public class RebuiltPriceDetailsResponse {
    int seasonNumber;
    int storedCount;
    int mismatchCount;
    List<String> mismatches;
    int unmatchedCount;
    List<String> unmatchedCards;
}

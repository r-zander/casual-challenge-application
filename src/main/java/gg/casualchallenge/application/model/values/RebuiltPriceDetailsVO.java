package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.util.List;

@Value
public class RebuiltPriceDetailsVO {
    int seasonNumber;
    int storedCount;
    int mismatchCount;
    List<String> mismatches; // the first 20
    int unmatchedCount;
    List<String> unmatchedCards; // the first 20
}

package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class MatchedPriceDetailsVO {
    Map<UUID, CardPriceDetailsVO> priceDetailsByOracleId;
    List<String> mismatches; // "name (stored 4, now 5)", the budget points the season has against what the archive prices the card at today
    List<String> unmatchedCards; // "name (stored 4)", budget points in the season but no card in the archive
}

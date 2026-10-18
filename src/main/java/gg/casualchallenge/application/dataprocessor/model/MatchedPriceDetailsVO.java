package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
import gg.casualchallenge.application.model.values.RebuiltPriceDetailsVO;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class MatchedPriceDetailsVO {
    Map<UUID, CardPriceDetailsVO> priceDetailsByOracleId;
    List<RebuiltPriceDetailsVO.MismatchVO> mismatches;
    List<RebuiltPriceDetailsVO.UnmatchedCardVO> unmatchedCards;
}

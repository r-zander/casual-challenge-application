package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
import lombok.Value;

import java.util.Map;

@Value
public class BudgetPointsVO {
    Map<String, Cents> pricesByCardName;
    /** No basics in here */
    Map<String, CardPriceDetailsVO> priceDetailsByCardName;
    double exchangeRate;
    double adjustedExchangeRate;
    int pricesFixedByExchangeRateCount;
}

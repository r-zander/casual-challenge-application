package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.util.Map;

@Value
public class BudgetPointsVO {
    Map<String, Integer> budgetPointsByCardName;
    double exchangeRate;
    double adjustedExchangeRate;
    int pricesFixedByExchangeRate;
}

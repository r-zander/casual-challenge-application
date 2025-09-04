package gg.casualchallenge.application.api.legacy.values;

import lombok.Value;

import java.util.UUID;

@Value
public class CardBudgetPointsVO {
    UUID cardOracleId;
    String cardName;
    Integer budgetPoints;
}

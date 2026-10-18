package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.util.Map;

@Value
public class MtgJsonPricesVO {
    Map<String, CardPrices> pricesByCardName;
    /** Days of the window that carried a price at all - a short season start prices on very little. */
    int pricedDays;
}

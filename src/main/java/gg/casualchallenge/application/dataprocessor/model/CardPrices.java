package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.dataprocessor.PriceSeries;
import lombok.Getter;

@Getter
public class CardPrices {

    private final PriceSeries eur;
    private final PriceSeries usd;

    public CardPrices(int windowLength) {
        this.eur = new PriceSeries(windowLength);
        this.usd = new PriceSeries(windowLength);
    }

}

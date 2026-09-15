package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.PriceSeries;

public final class BudgetPoints {

    public static Cents fromSeries(PriceSeries series) {
        return series.sumOfCheapest().average(series.pricedDays());
    }

    public static double adjustExchangeRate(double averageRate) {
        return (1 + averageRate) / 2; // to be defensive, the average exchange rate is reduced
    }

    public static Cents fromUsd(Cents usdPrice, double adjustedRate) {
        return usdPrice.dividedBy(adjustedRate);
    }

    private BudgetPoints() {}
}

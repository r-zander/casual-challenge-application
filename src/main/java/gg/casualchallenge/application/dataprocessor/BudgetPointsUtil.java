package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.model.values.CardPriceDetailsVO;

public final class BudgetPointsUtil {

    public static Cents fromSeries(PriceSeries series) {
        return series.sumOfCheapest().average(series.pricedDays());
    }

    public static double adjustExchangeRate(double averageRate) {
        // To be defensive, the average exchange rate is reduced
        return (1 + averageRate) / 2;
    }

    public static Cents fromUsd(Cents usdPrice, double adjustedRate) {
        return usdPrice.dividedBy(adjustedRate);
    }

    /** The budget points the way a reader of the explanation gets to them, from the document alone. */
    public static Cents fromPriceDetails(CardPriceDetailsVO priceDetails) {
        if (priceDetails.getPricedBy() == null) return Cents.of(0);

        switch (priceDetails.getPricedBy()) {
            case CARDMARKET:
                return Cents.of(priceDetails.getCardmarket().getAverageCents());
            case TCGPLAYER:
                return fromUsd(
                        Cents.of(priceDetails.getTcgplayer().getAverageCents()),
                        priceDetails.getAdjustedExchangeRate()
                );
            default:
                throw new IllegalArgumentException("Unknown price provider '" + priceDetails.getPricedBy() + "'.");
        }
    }

    private BudgetPointsUtil() {}
}

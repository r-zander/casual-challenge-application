package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetPointsUtilTest {

    @Test
    void testFromSeries() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new Cents[]{Cents.of(100), Cents.of(150), null});
        priceSeries.addPrinting(new Cents[]{Cents.of(120), Cents.of(300), Cents.of(90)});

        assertEquals(Cents.of(113), BudgetPointsUtil.fromSeries(priceSeries));
        assertEquals(Cents.of(0), BudgetPointsUtil.fromSeries(new PriceSeries(3)));
    }

    @Test
    void testAdjustExchangeRate() {
        assertEquals(1.0, BudgetPointsUtil.adjustExchangeRate(1.0));
        assertEquals(1.05, BudgetPointsUtil.adjustExchangeRate(1.1));
        assertEquals(0.5, BudgetPointsUtil.adjustExchangeRate(0));
    }

    @Test
    void testFromUsd() {
        assertEquals(Cents.of(100), BudgetPointsUtil.fromUsd(Cents.of(110), 1.1));
        assertEquals(Cents.of(0), BudgetPointsUtil.fromUsd(Cents.of(0), 1.1));
        assertEquals(Cents.of(2273), BudgetPointsUtil.fromUsd(Cents.of(2500), 1.1));
    }

}

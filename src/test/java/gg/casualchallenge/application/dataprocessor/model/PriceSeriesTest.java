package gg.casualchallenge.application.dataprocessor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceSeriesTest {

    @Test
    void testSumOfCheapest() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(new Cents[]{Cents.of(100), Cents.of(400), null, null});
        priceSeries.addPrinting(new Cents[]{Cents.of(200), Cents.of(300), Cents.of(500), null});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(0, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(900), priceSeries.sumOfCheapest());
        assertEquals(3, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withFlatPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new Cents[]{Cents.of(100), Cents.of(200), null});
        priceSeries.addPrinting(new Cents[]{Cents.of(50), Cents.of(50), Cents.of(50)});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(300), priceSeries.sumOfCheapest());
        assertEquals(2, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withOnlyFlatPrintings() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new Cents[]{Cents.of(200), Cents.of(200), null});
        priceSeries.addPrinting(new Cents[]{null, Cents.of(100), Cents.of(100)});

        assertEquals(2, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(400), priceSeries.sumOfCheapest());
        assertEquals(3, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withEmptyPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new Cents[]{Cents.of(100), Cents.of(100), Cents.of(100)});
        priceSeries.addPrinting(new Cents[]{null, null, null});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(0), priceSeries.sumOfCheapest());
        assertEquals(0, priceSeries.pricedDays());
    }

}

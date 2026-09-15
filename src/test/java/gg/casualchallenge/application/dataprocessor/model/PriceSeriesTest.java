package gg.casualchallenge.application.dataprocessor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceSeriesTest {

    @Test
    void testAverage() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(new double[]{1.0, 4.0, Double.NaN, Double.NaN});
        priceSeries.addPrinting(new double[]{2.0, 3.0, 5.0, Double.NaN});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(0, priceSeries.getFlatPrintingCount());
        assertEquals(3.0, priceSeries.average());
    }

    @Test
    void testAverage_withFlatPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new double[]{1.0, 2.0, Double.NaN});
        priceSeries.addPrinting(new double[]{0.5, 0.5, 0.5});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(1.5, priceSeries.average());
    }

    @Test
    void testAverage_withOnlyFlatPrintings() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new double[]{2.0, 2.0, Double.NaN});
        priceSeries.addPrinting(new double[]{Double.NaN, 1.0, 1.0});

        assertEquals(2, priceSeries.getFlatPrintingCount());
        assertEquals(4.0 / 3, priceSeries.average());
    }

    @Test
    void testAverage_withEmptyPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(new double[]{1.0, 1.0, 1.0});
        priceSeries.addPrinting(new double[]{Double.NaN, Double.NaN, Double.NaN});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(0.0, priceSeries.average());
    }

}

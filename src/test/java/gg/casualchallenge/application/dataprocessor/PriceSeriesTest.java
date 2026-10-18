package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrinting;
import gg.casualchallenge.application.dataprocessor.model.PrintingPrices;
import gg.casualchallenge.application.model.type.Finish;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceSeriesTest {

    private static final MtgJsonPrinting CHK_193 = new MtgJsonPrinting(null, null, "CHK", "193", true, true);
    private static final MtgJsonPrinting UMA_152 = new MtgJsonPrinting(null, null, "UMA", "152", true, true);
    private static final MtgJsonPrinting INR_404 = new MtgJsonPrinting(null, null, "INR", "404", true, true);
    private static final MtgJsonPrinting INR_175 = new MtgJsonPrinting(null, null, "INR", "175", true, true);
    private static final MtgJsonPrinting MP2_49 = new MtgJsonPrinting(null, null, "MP2", "49", true, false);
    private static final MtgJsonPrinting DELVER_INR_60_BACK = new MtgJsonPrinting(null, null, "INR", "60", true, true);
    private static final MtgJsonPrinting DELVER_INR_60_FRONT = new MtgJsonPrinting(null, null, "INR", "60", true, true);

    @Test
    void testSumOfCheapest() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{Cents.of(100), Cents.of(400), null, null});
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{Cents.of(200), Cents.of(300), Cents.of(500), null});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(0, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(900), priceSeries.sumOfCheapest());
        assertEquals(3, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withFlatPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{Cents.of(100), Cents.of(200), null});
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{Cents.of(50), Cents.of(50), Cents.of(50)});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(300), priceSeries.sumOfCheapest());
        assertEquals(2, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withOnlyFlatPrintings() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{Cents.of(200), Cents.of(200), null});
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{null, Cents.of(100), Cents.of(100)});

        assertEquals(2, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(400), priceSeries.sumOfCheapest());
        assertEquals(3, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withEmptyPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{Cents.of(100), Cents.of(100), Cents.of(100)});
        priceSeries.addPrinting(null, Finish.NONFOIL, new Cents[]{null, null, null});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(0), priceSeries.sumOfCheapest());
        assertEquals(0, priceSeries.pricedDays());
    }

    @Test
    void testCheapestDays() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(UMA_152, Finish.NONFOIL, cents(120, 120, 110, 110));
        priceSeries.addPrinting(INR_404, Finish.NONFOIL, cents(82, 82, 85, 85));
        priceSeries.addPrinting(INR_175, Finish.NONFOIL, cents(83, 83, 75, 70));

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();

        assertEquals(0, printingPrices.get(0).getCheapestDays());
        assertEquals(2, printingPrices.get(1).getCheapestDays());
        assertEquals(2, printingPrices.get(2).getCheapestDays());
        assertEquals(4, printingPrices.get(2).getPricedDays());
        assertEquals(70, printingPrices.get(2).getLowestCents());
        assertEquals(83, printingPrices.get(2).getHighestCents());
        assertEquals(311, printingPrices.get(2).getSumOfCents());
        assertFalse(printingPrices.get(2).isFlat());
        assertArrayEquals(cents(82, 82, 75, 70), priceSeries.cheapestPriceOfEveryDay());
        assertArrayEquals(new int[]{1, 1, 2, 2}, priceSeries.cheapestPrintingOfEveryDay());
        assertFalse(priceSeries.hasOnlyFlatPrintings());
    }

    @Test
    void testCheapestDays_withTie() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(DELVER_INR_60_BACK, Finish.NONFOIL, cents(31, 29, 28));
        priceSeries.addPrinting(DELVER_INR_60_FRONT, Finish.NONFOIL, cents(31, 29, 28));

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();

        assertEquals(3, printingPrices.get(0).getCheapestDays());
        assertEquals(0, printingPrices.get(1).getCheapestDays());
        assertArrayEquals(new int[]{0, 0, 0}, priceSeries.cheapestPrintingOfEveryDay());
    }

    @Test
    void testCheapestDays_withFlatPrintings() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(UMA_152, Finish.NONFOIL, cents(83, 82, null, 81));
        priceSeries.addPrinting(UMA_152, Finish.FOIL, cents(5, 5, null, 5));
        priceSeries.addPrinting(INR_404, Finish.NONFOIL, cents(99, 99, null, 99));
        priceSeries.addPrinting(INR_175, Finish.NONFOIL, cents(74, 74, null, 73));
        PriceSeries flatPriceSeries = new PriceSeries(4);
        flatPriceSeries.addPrinting(CHK_193, Finish.FOIL, cents(1360, 1360, null, 1360));
        flatPriceSeries.addPrinting(UMA_152, Finish.FOIL, cents(5, 5, null, 5));

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();
        List<PrintingPrices> flatPrintingPrices = flatPriceSeries.printingPrices();

        assertTrue(printingPrices.get(1).isFlat());
        assertEquals(5, printingPrices.get(1).getLowestCents());
        assertEquals(0, printingPrices.get(1).getCheapestDays());
        assertEquals(0, printingPrices.get(2).getCheapestDays());
        assertEquals(3, printingPrices.get(3).getCheapestDays());
        assertArrayEquals(cents(74, 74, null, 73), priceSeries.cheapestPriceOfEveryDay());
        assertEquals(3, priceSeries.cheapestPrintingOfEveryDay()[0]);
        assertEquals(3, priceSeries.cheapestPrintingOfEveryDay()[3]);
        assertFalse(priceSeries.hasOnlyFlatPrintings());

        assertEquals(0, flatPrintingPrices.get(0).getCheapestDays());
        assertEquals(3, flatPrintingPrices.get(1).getCheapestDays());
        assertArrayEquals(cents(5, 5, null, 5), flatPriceSeries.cheapestPriceOfEveryDay());
        assertEquals(1, flatPriceSeries.cheapestPrintingOfEveryDay()[0]);
        assertEquals(1, flatPriceSeries.cheapestPrintingOfEveryDay()[3]);
        assertTrue(flatPriceSeries.hasOnlyFlatPrintings());
    }

    @Test
    void testCheapestDays_withoutPriceInWindow() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(UMA_152, Finish.FOIL, cents(5, 5, 5));
        priceSeries.addPrinting(MP2_49, Finish.FOIL, cents(null, null, null));

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();

        assertEquals(2, printingPrices.size());
        assertEquals(0, printingPrices.get(1).getPricedDays());
        assertEquals(0, printingPrices.get(1).getCheapestDays());
        assertFalse(printingPrices.get(1).isFlat());
        assertTrue(printingPrices.get(0).isFlat());
        assertEquals(0, printingPrices.get(0).getCheapestDays());
        assertFalse(priceSeries.hasOnlyFlatPrintings());
        assertArrayEquals(cents(null, null, null), priceSeries.cheapestPriceOfEveryDay());
    }

    private static Cents[] cents(Integer... amounts) {
        Cents[] centsPerDay = new Cents[amounts.length];
        for (int day = 0; day < amounts.length; day++) {
            if (amounts[day] != null) centsPerDay[day] = Cents.of(amounts[day]);
        }

        return centsPerDay;
    }

}

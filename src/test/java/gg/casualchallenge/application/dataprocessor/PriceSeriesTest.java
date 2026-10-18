package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrinting;
import gg.casualchallenge.application.dataprocessor.model.PrintingPrices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceSeriesTest {

    private static final MtgJsonPrinting CHK_193 = new MtgJsonPrinting(UUID.fromString("740d74ee-384a-5bd5-8fd2-a80d018956c3"), "Through the Breach", "CHK", "193", true, true);
    private static final MtgJsonPrinting UMA_152 =new MtgJsonPrinting(UUID.fromString("7adf1e71-2477-51ae-8350-ea498447e8b9"), "Through the Breach", "UMA", "152", true, true);
    private static final MtgJsonPrinting INR_404 = new MtgJsonPrinting(UUID.fromString("a2d78b0c-a313-5e71-9a46-0c559aa97675"), "Through the Breach", "INR", "404", true, true);
    private static final MtgJsonPrinting INR_175 = new MtgJsonPrinting(UUID.fromString("da86c63a-9f78-5e93-a8b8-ce39468f4826"), "Through the Breach", "INR", "175", true, true);
    private static final MtgJsonPrinting MP2_49 = new MtgJsonPrinting(UUID.fromString("aaad4f98-a51b-5f96-9223-13cbbe0638b1"), "Through the Breach", "MP2", "49", true, false);
    private static final MtgJsonPrinting DELVER_INR_60_BACK = new MtgJsonPrinting(UUID.fromString("2bae222c-f8ef-50ce-b41e-ce5583c11b12"), "Delver of Secrets // Insectile Aberration", "INR", "60", true, true);
    private static final MtgJsonPrinting DELVER_INR_60_FRONT = new MtgJsonPrinting(UUID.fromString("be445963-f15f-5538-8a32-81e965409e65"), "Delver of Secrets // Insectile Aberration", "INR", "60", true, true);

    @Test
    void testSumOfCheapest() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(null, false, new Cents[]{Cents.of(100), Cents.of(400), null, null});
        priceSeries.addPrinting(null, false, new Cents[]{Cents.of(200), Cents.of(300), Cents.of(500), null});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(0, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(900), priceSeries.sumOfCheapest());
        assertEquals(3, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withFlatPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(null, false, new Cents[]{Cents.of(100), Cents.of(200), null});
        priceSeries.addPrinting(null, false, new Cents[]{Cents.of(50), Cents.of(50), Cents.of(50)});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(300), priceSeries.sumOfCheapest());
        assertEquals(2, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withOnlyFlatPrintings() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(null, false, new Cents[]{Cents.of(200), Cents.of(200), null});
        priceSeries.addPrinting(null, false, new Cents[]{null, Cents.of(100), Cents.of(100)});

        assertEquals(2, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(400), priceSeries.sumOfCheapest());
        assertEquals(3, priceSeries.pricedDays());
    }

    @Test
    void testSumOfCheapest_withEmptyPrinting() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(null, false, new Cents[]{Cents.of(100), Cents.of(100), Cents.of(100)});
        priceSeries.addPrinting(null, false, new Cents[]{null, null, null});

        assertEquals(2, priceSeries.getPrintingCount());
        assertEquals(1, priceSeries.getFlatPrintingCount());
        assertEquals(Cents.of(0), priceSeries.sumOfCheapest());
        assertEquals(0, priceSeries.pricedDays());
    }

    @Test
    void testCheapestDays() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(UMA_152, false, new Cents[]{Cents.of(120), Cents.of(120), Cents.of(110), Cents.of(110)});
        priceSeries.addPrinting(INR_404, false, new Cents[]{Cents.of(82), Cents.of(82), Cents.of(85), Cents.of(85)});
        priceSeries.addPrinting(INR_175, false, new Cents[]{Cents.of(83), Cents.of(83), Cents.of(75), Cents.of(70)});

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();

        assertEquals(0, printingPrices.get(0).getCheapestDays());
        assertEquals(2, printingPrices.get(1).getCheapestDays());
        assertEquals(2, printingPrices.get(2).getCheapestDays());
        assertEquals(4, printingPrices.get(2).getPricedDays());
        assertEquals(70, printingPrices.get(2).getLowestCents());
        assertEquals(83, printingPrices.get(2).getHighestCents());
        assertEquals(311, printingPrices.get(2).getSumOfCents());
        assertFalse(printingPrices.get(2).isFlat());
        assertArrayEquals(new Cents[]{Cents.of(82), Cents.of(82), Cents.of(75), Cents.of(70)}, priceSeries.cheapestPriceOfEveryDay());
        assertArrayEquals(new int[]{1, 1, 2, 2}, priceSeries.cheapestPrintingOfEveryDay());
        assertFalse(priceSeries.hasOnlyFlatPrintings());
    }

    @Test
    void testCheapestDays_withTie() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(DELVER_INR_60_BACK, false, new Cents[]{Cents.of(31), Cents.of(29), Cents.of(28)});
        priceSeries.addPrinting(DELVER_INR_60_FRONT, false, new Cents[]{Cents.of(31), Cents.of(29), Cents.of(28)});

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();

        assertEquals(3, printingPrices.get(0).getCheapestDays());
        assertEquals(0, printingPrices.get(1).getCheapestDays());
        assertArrayEquals(new int[]{0, 0, 0}, priceSeries.cheapestPrintingOfEveryDay());
    }

    @Test
    void testCheapestDays_withFlatPrintings() {
        PriceSeries priceSeries = new PriceSeries(4);
        priceSeries.addPrinting(UMA_152, false, new Cents[]{Cents.of(83), Cents.of(82), null, Cents.of(81)});
        priceSeries.addPrinting(UMA_152, true, new Cents[]{Cents.of(5), Cents.of(5), null, Cents.of(5)});
        priceSeries.addPrinting(INR_404, false, new Cents[]{Cents.of(99), Cents.of(99), null, Cents.of(99)});
        priceSeries.addPrinting(INR_175, false, new Cents[]{Cents.of(74), Cents.of(74), null, Cents.of(73)});
        PriceSeries flatPriceSeries = new PriceSeries(4);
        flatPriceSeries.addPrinting(CHK_193, true, new Cents[]{Cents.of(1360), Cents.of(1360), null, Cents.of(1360)});
        flatPriceSeries.addPrinting(UMA_152, true, new Cents[]{Cents.of(5), Cents.of(5), null, Cents.of(5)});

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();
        List<PrintingPrices> flatPrintingPrices = flatPriceSeries.printingPrices();

        assertTrue(printingPrices.get(1).isFlat());
        assertEquals(5, printingPrices.get(1).getLowestCents());
        assertEquals(0, printingPrices.get(1).getCheapestDays());
        assertEquals(0, printingPrices.get(2).getCheapestDays());
        assertEquals(3, printingPrices.get(3).getCheapestDays());
        assertArrayEquals(new Cents[]{Cents.of(74), Cents.of(74), null, Cents.of(73)}, priceSeries.cheapestPriceOfEveryDay());
        assertEquals(3, priceSeries.cheapestPrintingOfEveryDay()[0]);
        assertEquals(3, priceSeries.cheapestPrintingOfEveryDay()[3]);
        assertFalse(priceSeries.hasOnlyFlatPrintings());

        assertEquals(0, flatPrintingPrices.get(0).getCheapestDays());
        assertEquals(3, flatPrintingPrices.get(1).getCheapestDays());
        assertArrayEquals(new Cents[]{Cents.of(5), Cents.of(5), null, Cents.of(5)}, flatPriceSeries.cheapestPriceOfEveryDay());
        assertEquals(1, flatPriceSeries.cheapestPrintingOfEveryDay()[0]);
        assertEquals(1, flatPriceSeries.cheapestPrintingOfEveryDay()[3]);
        assertTrue(flatPriceSeries.hasOnlyFlatPrintings());
    }

    @Test
    void testCheapestDays_withoutPriceInWindow() {
        PriceSeries priceSeries = new PriceSeries(3);
        priceSeries.addPrinting(UMA_152, true, new Cents[]{Cents.of(5), Cents.of(5), Cents.of(5)});
        priceSeries.addPrinting(MP2_49, true, new Cents[]{null, null, null});

        List<PrintingPrices> printingPrices = priceSeries.printingPrices();

        assertEquals(2, printingPrices.size());
        assertEquals(0, printingPrices.get(1).getPricedDays());
        assertEquals(0, printingPrices.get(1).getCheapestDays());
        assertFalse(printingPrices.get(1).isFlat());
        assertTrue(printingPrices.get(0).isFlat());
        assertEquals(0, printingPrices.get(0).getCheapestDays());
        assertFalse(priceSeries.hasOnlyFlatPrintings());
        assertArrayEquals(new Cents[]{null, null, null}, priceSeries.cheapestPriceOfEveryDay());
    }

}

package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrinting;
import gg.casualchallenge.application.dataprocessor.model.PrintingPrices;
import gg.casualchallenge.application.model.type.Finish;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// One instance per card and market, folding every printing in as it streams by, so the gigabyte of AllPrices.json never sits in memory
public class PriceSeries {

    private static final long NO_PRICE = -1; // no card ever costs a negative amount of cents

    @Getter
    private int printingCount;
    @Getter
    private int flatPrintingCount;
    private final long[] cheapestPerDay; // 33k cards hold four of these, so the running minimums stay primitive
    private final long[] cheapestFlatPerDay;
    /** Index into {@code printingPrices}, only means something on a day with a price */
    private final int[] cheapestPrintingPerDay;
    private final int[] cheapestFlatPrintingPerDay;
    private final List<PrintingPrices> printingPrices = new ArrayList<>();

    public PriceSeries(int windowLength) {
        this.cheapestPerDay = new long[windowLength];
        this.cheapestFlatPerDay = new long[windowLength];
        this.cheapestPrintingPerDay = new int[windowLength];
        this.cheapestFlatPrintingPerDay = new int[windowLength];
        Arrays.fill(this.cheapestPerDay, NO_PRICE);
        Arrays.fill(this.cheapestFlatPerDay, NO_PRICE);
    }

    public void addPrinting(MtgJsonPrinting printing, Finish finish, Cents[] centsPerDay) {
        printingCount++;

        Cents knownPrice = null;
        boolean areAllPricesEqual = true;
        for (Cents price : centsPerDay) {
            if (price == null) continue;
            if (knownPrice == null) {
                knownPrice = price;
            } else if (!knownPrice.equals(price)) {
                areAllPricesEqual = false;
                break;
            }
        }
        boolean isFlat = areAllPricesEqual;
        if (knownPrice == null) isFlat = false;
        int printingIndex = printingPrices.size();
        printingPrices.add(PrintingPrices.of(printing, finish, centsPerDay, isFlat));
        // A printing without a single price in our window is not an anomaly, it just doesn't count for anything
        if (knownPrice == null) return;

        long[] cheapest = areAllPricesEqual ? cheapestFlatPerDay : cheapestPerDay;
        int[] cheapestPrinting = areAllPricesEqual ? cheapestFlatPrintingPerDay : cheapestPrintingPerDay;
        if (areAllPricesEqual) flatPrintingCount++;
        for (int day = 0; day < centsPerDay.length; day++) {
            Cents price = centsPerDay[day];
            if (price == null) continue;
            if (cheapest[day] == NO_PRICE || price.isLessThan(Cents.of(cheapest[day]))) {
                cheapest[day] = price.getAmount();
                cheapestPrinting[day] = printingIndex;
            }
        }
    }

    public Cents sumOfCheapest() {
        Cents sum = Cents.of(0);
        for (long price : cheapestOfEveryDay()) {
            if (price == NO_PRICE) continue;
            sum = sum.plus(Cents.of(price));
        }

        return sum;
    }

    public int pricedDays() {
        int dayCount = 0;
        for (long price : cheapestOfEveryDay()) {
            if (price == NO_PRICE) continue;
            dayCount++;
        }

        return dayCount;
    }

    /**
     * The cheapest days are counted on the days that make the price,
     * so a flat printing only gets some when every printing is flat.
     */
    public List<PrintingPrices> printingPrices() {
        long[] cheapestOfEveryDay = cheapestOfEveryDay();
        int[] cheapestPrintingOfEveryDay = cheapestPrintingOfEveryDay();
        int[] cheapestDays = new int[printingPrices.size()];
        for (int day = 0; day < cheapestOfEveryDay.length; day++) {
            if (cheapestOfEveryDay[day] == NO_PRICE) continue;
            cheapestDays[cheapestPrintingOfEveryDay[day]]++;
        }

        List<PrintingPrices> printingPricesWithCheapestDays = new ArrayList<>(printingPrices.size());
        for (int printingIndex = 0; printingIndex < printingPrices.size(); printingIndex++) {
            PrintingPrices printing = printingPrices.get(printingIndex);
            printingPricesWithCheapestDays.add(printing.withCheapestDays(cheapestDays[printingIndex]));
        }

        return printingPricesWithCheapestDays;
    }

    public Cents[] cheapestPriceOfEveryDay() {
        long[] cheapestOfEveryDay = cheapestOfEveryDay();
        Cents[] cheapestPriceOfEveryDay = new Cents[cheapestOfEveryDay.length];
        for (int day = 0; day < cheapestOfEveryDay.length; day++) {
            if (cheapestOfEveryDay[day] == NO_PRICE) continue;
            cheapestPriceOfEveryDay[day] = Cents.of(cheapestOfEveryDay[day]);
        }

        return cheapestPriceOfEveryDay;
    }

    public int[] cheapestPrintingOfEveryDay() {
        return flatPrintingCount < printingCount ? cheapestPrintingPerDay : cheapestFlatPrintingPerDay;
    }

    public boolean hasOnlyFlatPrintings() {
        if (printingCount == 0) return false;

        return flatPrintingCount == printingCount;
    }

    // A price that never moves is an anomaly, so the flat printings only count when there is no other printing to fall back on
    private long[] cheapestOfEveryDay() {
        return flatPrintingCount < printingCount ? cheapestPerDay : cheapestFlatPerDay;
    }

}

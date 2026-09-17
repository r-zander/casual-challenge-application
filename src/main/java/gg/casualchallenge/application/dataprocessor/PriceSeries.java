package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Cents;
import lombok.Getter;

import java.util.Arrays;

// One instance per card and market, folding every printing in as it streams by, so the gigabyte of AllPrices.json never sits in memory
public class PriceSeries {

    private static final long NO_PRICE = -1; // no card ever costs a negative amount of cents

    @Getter
    private int printingCount;
    @Getter
    private int flatPrintingCount;
    private final long[] cheapestPerDay; // 33k cards hold four of these, so the running minimums stay primitive
    private final long[] cheapestFlatPerDay;

    public PriceSeries(int windowLength) {
        this.cheapestPerDay = new long[windowLength];
        this.cheapestFlatPerDay = new long[windowLength];
        Arrays.fill(this.cheapestPerDay, NO_PRICE);
        Arrays.fill(this.cheapestFlatPerDay, NO_PRICE);
    }

    public void addPrinting(Cents[] centsPerDay) {
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
        // A printing without a single price in our window is not an anomaly, it just doesn't count for anything
        if (knownPrice == null) return;

        long[] cheapest = areAllPricesEqual ? cheapestFlatPerDay : cheapestPerDay;
        if (areAllPricesEqual) flatPrintingCount++;
        for (int day = 0; day < centsPerDay.length; day++) {
            Cents price = centsPerDay[day];
            if (price == null) continue;
            if (cheapest[day] == NO_PRICE || price.isLessThan(Cents.of(cheapest[day]))) {
                cheapest[day] = price.getAmount();
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

    // A price that never moves is an anomaly, so the flat printings only count when there is no other printing to fall back on
    private long[] cheapestOfEveryDay() {
        return flatPrintingCount < printingCount ? cheapestPerDay : cheapestFlatPerDay;
    }

}

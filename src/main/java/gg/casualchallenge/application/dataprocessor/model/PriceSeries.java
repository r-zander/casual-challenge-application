package gg.casualchallenge.application.dataprocessor.model;

import lombok.Getter;

import java.util.Arrays;

@Getter
public class PriceSeries {

    private int printingCount;
    private int flatPrintingCount;
    private final double[] cheapestPerDay;
    private final double[] cheapestFlatPerDay;

    public PriceSeries(int windowLength) {
        this.cheapestPerDay = new double[windowLength];
        this.cheapestFlatPerDay = new double[windowLength];
        Arrays.fill(this.cheapestPerDay, Double.NaN);
        Arrays.fill(this.cheapestFlatPerDay, Double.NaN);
    }

    public void addPrinting(double[] pricesPerDay) {
        printingCount++;

        double knownPrice = Double.NaN;
        boolean areAllPricesEqual = true;
        for (double price : pricesPerDay) {
            if (Double.isNaN(price)) continue;
            if (Double.isNaN(knownPrice)) {
                knownPrice = price;
            } else if (knownPrice != price) {
                areAllPricesEqual = false;
                break;
            }
        }
        // A printing without a single price in our window is not an anomaly, it just doesn't count for anything
        if (Double.isNaN(knownPrice)) return;

        double[] cheapest = areAllPricesEqual ? cheapestFlatPerDay : cheapestPerDay;
        if (areAllPricesEqual) flatPrintingCount++;
        for (int day = 0; day < pricesPerDay.length; day++) {
            double price = pricesPerDay[day];
            if (Double.isNaN(price)) continue;
            if (Double.isNaN(cheapest[day]) || cheapest[day] > price) {
                cheapest[day] = price;
            }
        }
    }

    public double average() {
        // All prices of a printing being the same is an anomaly - as long as there is at least one printing left without it
        double[] cheapest = flatPrintingCount < printingCount ? cheapestPerDay : cheapestFlatPerDay;
        double sum = 0;
        int dayCount = 0;
        for (double price : cheapest) {
            if (Double.isNaN(price)) continue;
            sum += price;
            dayCount++;
        }
        if (dayCount == 0) return 0;
        return sum / dayCount;
    }

}

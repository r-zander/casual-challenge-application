package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;
import lombok.With;

// One printing in one finish over the whole price window, the cents stay 0 without a single price in there
@Value
public class PrintingPrices {
    MtgJsonPrinting printing;
    boolean foil;
    int pricedDays;
    @With
    int cheapestDays; // only known once every printing is in, see PriceSeries.printingPrices()
    long lowestCents;
    long highestCents;
    long sumOfCents;
    boolean flat;

    public static PrintingPrices of(MtgJsonPrinting printing, boolean foil, Cents[] centsPerDay, boolean flat) {
        int pricedDays = 0;
        long lowestCents = 0;
        long highestCents = 0;
        long sumOfCents = 0;
        for (Cents price : centsPerDay) {
            if (price == null) continue;
            if (pricedDays == 0 || price.getAmount() < lowestCents) lowestCents = price.getAmount();
            if (pricedDays == 0 || price.getAmount() > highestCents) highestCents = price.getAmount();
            sumOfCents += price.getAmount();
            pricedDays++;
        }

        return new PrintingPrices(printing, foil, pricedDays, 0, lowestCents, highestCents, sumOfCents, flat);
    }
}

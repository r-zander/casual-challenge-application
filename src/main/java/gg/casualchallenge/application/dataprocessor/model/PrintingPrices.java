package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.type.Finish;
import lombok.Value;
import lombok.With;

/** One printing in one finish over the whole price window, the cents stay 0 without a single price in there. */
@Value
public class PrintingPrices {
    MtgJsonPrinting printing;
    Finish finish;
    int pricedDays;
    /** Only known once every printing is in, see {@code PriceSeries.printingPrices()} */
    @With
    int cheapestDays;
    long lowestCents;
    long highestCents;
    long sumOfCents;
    boolean flat;

    public static PrintingPrices of(MtgJsonPrinting printing, Finish finish, Cents[] centsPerDay, boolean flat) {
        int pricedDays = 0;
        long lowestCents = 0;
        long highestCents = 0;
        long sumOfCents = 0;
        for (Cents price : centsPerDay) {
            if (price == null) continue;
            if (pricedDays == 0) {
                lowestCents = price.getAmount();
                highestCents = price.getAmount();
            }
            lowestCents = Math.min(lowestCents, price.getAmount());
            highestCents = Math.max(highestCents, price.getAmount());
            sumOfCents += price.getAmount();
            pricedDays++;
        }

        return new PrintingPrices(printing, finish, pricedDays, 0, lowestCents, highestCents, sumOfCents, flat);
    }
}

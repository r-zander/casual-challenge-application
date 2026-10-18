package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.Finish;
import gg.casualchallenge.application.model.type.PriceProvider;
import lombok.Value;
import lombok.With;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>Debugging of budget points for a single card.</p>
 *
 * <p>This is stored as JSON next to the season data of a card.</p>
 */
@Value
public class CardPriceDetailsVO {
    LocalDate priceWindowStart;
    LocalDate priceWindowEnd;
    /**
     * {@code CARDMARKET}, or {@code TCGPLAYER} when Cardmarket came to 0 and the USD price stepped in,
     * null without any price at all
     */
    @With
    PriceProvider pricedBy;
    /** Only with {@code TCGPLAYER} */
    @With
    Double adjustedExchangeRate;
    MarketPricesVO cardmarket;
    MarketPricesVO tcgplayer;
    List<IgnoredPrintingVO> ignoredPrintings;

    @Value
    public static class MarketPricesVO {
        int pricedDays;
        int averageCents;
        boolean onlyFlatPrintings;
        /** Sorted by {@code cheapestDays}, most first, then by {@code averageCents}. */
        List<PrintingPriceVO> printings;
        /** One per day of the window, null on a day without a price */
        List<Integer> cheapestCentsPerDay;
        /** Index into {@code printings} */
        List<Integer> cheapestPrintingPerDay;
    }

    @Value
    public static class PrintingPriceVO {
        String setCode;
        String setName;
        String number;
        Finish finish;
        int pricedDays;
        @With
        int cheapestDays;
        Integer lowestCents;
        Integer highestCents;
        Integer averageCents;
        boolean flat;
    }

    @Value
    public static class IgnoredPrintingVO {
        String setCode;
        String number;
        Finish finish;
    }

}

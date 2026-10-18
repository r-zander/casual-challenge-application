package gg.casualchallenge.application.model.values;

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
    @With
    String pricedBy; // cardmarket, tcgplayer when cardmarket came to 0 and the USD price stepped in, null without any price at all
    @With
    Double adjustedExchangeRate; // only with tcgplayer
    MarketPricesVO cardmarket;
    MarketPricesVO tcgplayer;
    List<IgnoredPrintingVO> ignoredPrintings;

    @Value
    public static class MarketPricesVO {
        int pricedDays;
        int averageCents;
        boolean onlyFlatPrintings;
        List<PrintingPriceVO> printings; // the one that was the cheapest most often first
        List<Integer> cheapestCentsPerDay; // one per day of the window, null on a day without a price
        List<Integer> cheapestPrintingPerDay; // index into printings
    }

    @Value
    public static class PrintingPriceVO {
        String setCode;
        String setName;
        String number;
        String finish;
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
        String finish;
    }

}

package gg.casualchallenge.application.api.datamodel;

import gg.casualchallenge.application.model.type.Legality;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value // same field names as CardExplanationVO and CardPriceDetailsVO, only the stored day arrays come out as days
public class CardExplanationResponse {
    String name;
    UUID oracleId;
    int seasonNumber;
    Integer budgetPoints;
    Legality legality;
    CardDTO.ReasonDTO reason;
    PriceDetailsDTO priceDetails;

    @Value
    public static class PriceDetailsDTO {
        LocalDate priceWindowStart;
        LocalDate priceWindowEnd;
        String pricedBy;
        Double adjustedExchangeRate;
        MarketPricesDTO cardmarket;
        MarketPricesDTO tcgplayer;
        List<IgnoredPrintingDTO> ignoredPrintings;
    }

    @Value
    public static class MarketPricesDTO {
        int pricedDays;
        int averageCents;
        boolean onlyFlatPrintings;
        List<PrintingPriceDTO> printings;
        List<DayPriceDTO> days;
    }

    @Value
    public static class PrintingPriceDTO {
        String setCode;
        String setName;
        String number;
        String finish;
        int pricedDays;
        int cheapestDays;
        Integer lowestCents;
        Integer highestCents;
        Integer averageCents;
        boolean flat;
    }

    @Value
    public static class DayPriceDTO { // the price that went into the average that day and the printing it came from, all null without a price
        LocalDate date;
        Integer cents;
        String setCode;
        String number;
        String finish;
    }

    @Value
    public static class IgnoredPrintingDTO {
        String setCode;
        String number;
        String finish;
    }

}

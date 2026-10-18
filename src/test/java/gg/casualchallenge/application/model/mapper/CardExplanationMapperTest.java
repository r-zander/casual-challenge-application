package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.CardExplanationResponse;
import gg.casualchallenge.application.model.type.AppliedRule;
import gg.casualchallenge.application.model.type.Finish;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.type.PriceProvider;
import gg.casualchallenge.application.model.values.CardExplanationVO;
import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
import gg.casualchallenge.application.model.values.CardWithDataVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CardExplanationMapperTest {

    private static final UUID THROUGH_THE_BREACH = UUID.fromString("c207aaee-0b67-4520-ad02-2c289228be2a");

    private static final List<Integer> THROUGH_THE_BREACH_CARDMARKET_CENTS = Arrays.asList(
            5, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, null, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 5, 5, 5, null, 5, null, null, null,
            5, 5, 5, 5, 2, 2, 2, 2, 2, 2);

    @Test
    void testToResponse() {
        List<Integer> cheapestPrintingPerDay = new ArrayList<>(THROUGH_THE_BREACH_CARDMARKET_CENTS.size());
        for (Integer cents : THROUGH_THE_BREACH_CARDMARKET_CENTS) {
            cheapestPrintingPerDay.add(cents != null ? 0 : null);
        }
        List<CardPriceDetailsVO.PrintingPriceVO> printings = List.of(
                printing("UMA", "Ultimate Masters", "152", Finish.FOIL, 65, 65, 2, 5, 4),
                printing("UMA", "Ultimate Masters", "152", Finish.NONFOIL, 65, 0, 30, 120, 82),
                printing("INR", "Innistrad Remastered", "175", Finish.NONFOIL, 65, 0, 58, 118, 85)
        );
        CardPriceDetailsVO.MarketPricesVO cardmarket = new CardPriceDetailsVO.MarketPricesVO(
                65,
                4,
                false,
                printings,
                THROUGH_THE_BREACH_CARDMARKET_CENTS,
                cheapestPrintingPerDay
        );
        CardPriceDetailsVO.MarketPricesVO tcgplayer =
                new CardPriceDetailsVO.MarketPricesVO(0, 0, false, List.of(), List.of(), List.of());
        CardWithDataVO.ReasonVO reason = new CardWithDataVO.ReasonVO(
                EnumSet.of(AppliedRule.NO_BANS, AppliedRule.VINTAGE_LEGAL),
                Map.of(),
                EnumSet.noneOf(MtgFormat.class)
        );
        CardPriceDetailsVO priceDetails = new CardPriceDetailsVO(
                LocalDate.of(2026, 7, 5),
                LocalDate.of(2026, 9, 13),
                PriceProvider.CARDMARKET,
                null,
                cardmarket,
                tcgplayer,
                List.of()
        );
        CardExplanationVO explanation = new CardExplanationVO(
                "Through the Breach",
                THROUGH_THE_BREACH,
                21,
                4,
                Legality.LEGAL,
                reason,
                priceDetails
        );

        CardExplanationResponse response = CardExplanationMapper.INSTANCE.toResponse(explanation);
        List<CardExplanationResponse.DayPriceDTO> days = response.getPriceDetails().getCardmarket().getDays();

        assertEquals(3, response.getPriceDetails().getCardmarket().getPrintings().size());
        assertEquals(70, days.size());
        assertEquals(LocalDate.of(2026, 7, 5), days.get(0).getDate());
        assertEquals(LocalDate.of(2026, 7, 6), days.get(1).getDate());
        assertEquals(2, days.get(1).getCents());
        assertEquals("UMA", days.get(1).getSetCode());
        assertEquals("152", days.get(1).getNumber());
        assertEquals(Finish.FOIL, days.get(1).getFinish());
        assertEquals(LocalDate.of(2026, 8, 6), days.get(32).getDate());
        assertNull(days.get(32).getCents());
        assertNull(days.get(32).getSetCode());
        assertNull(days.get(32).getFinish());
        assertEquals(LocalDate.of(2026, 9, 12), days.get(69).getDate());
        assertEquals(0, response.getPriceDetails().getTcgplayer().getDays().size());
    }

    @Test
    void testToDTO_withSeveralPrintings() {
        List<CardPriceDetailsVO.PrintingPriceVO> printings = List.of(
                printing("MID", "Innistrad: Midnight Hunt", "47", Finish.NONFOIL, 5, 3, 44, 50, 47),
                printing("INR", "Innistrad Remastered", "60", Finish.NONFOIL, 5, 2, 46, 55, 49),
                printing("ISD", "Innistrad", "51", Finish.NONFOIL, 5, 0, 52, 63, 57)
        );
        CardPriceDetailsVO.MarketPricesVO cardmarket = new CardPriceDetailsVO.MarketPricesVO(
                5,
                46,
                false,
                printings,
                List.of(46, 45, 48, 46, 44),
                List.of(0, 0, 1, 1, 0)
        );

        List<CardExplanationResponse.DayPriceDTO> days =
                CardExplanationMapper.INSTANCE.toDTO(cardmarket, LocalDate.of(2026, 7, 30)).getDays();

        assertEquals("MID", days.get(1).getSetCode());
        assertEquals("INR", days.get(2).getSetCode());
        assertEquals("60", days.get(3).getNumber());
        assertEquals(46, days.get(3).getCents());
        assertEquals("MID", days.get(4).getSetCode());
        assertEquals(LocalDate.of(2026, 8, 3), days.get(4).getDate());
    }

    private static CardPriceDetailsVO.PrintingPriceVO printing(
            String setCode,
            String setName,
            String number,
            Finish finish,
            int pricedDays,
            int cheapestDays,
            int lowestCents,
            int highestCents,
            int averageCents
    ) {
        return new CardPriceDetailsVO.PrintingPriceVO(
                setCode,
                setName,
                number,
                finish,
                pricedDays,
                cheapestDays,
                lowestCents,
                highestCents,
                averageCents,
                false
        );
    }

}

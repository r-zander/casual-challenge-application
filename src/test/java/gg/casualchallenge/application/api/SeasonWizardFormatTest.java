package gg.casualchallenge.application.api;

import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.type.MtgSetType;
import gg.casualchallenge.application.model.values.MtgSetVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonWizardFormatTest {

    private final SeasonWizardFormat format = new SeasonWizardFormat();

    @Test
    void testDate() {
        assertEquals("12 Sep 2026", format.date(LocalDate.of(2026, 9, 12)));
        assertEquals("1 May 2026", format.date(LocalDate.of(2026, 5, 1)));
        assertEquals("5 Aug 1993", format.date(LocalDate.of(1993, 8, 5)));
        assertEquals("28 Nov 2026", format.date(LocalDate.of(2026, 11, 28)));
        assertEquals("", format.date(null));
    }

    @Test
    void testDateTime() {
        assertEquals("12 Sep 2026, 07:05", format.dateTime(LocalDateTime.of(2026, 9, 12, 7, 5, 17, 395759241)));
        assertEquals("1 Jan 2026, 00:00", format.dateTime(LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertEquals("20 Sep 2026, 13:07", format.dateTime(LocalDateTime.of(2026, 9, 20, 13, 7, 59)));
        assertEquals("", format.dateTime(null));
    }

    @Test
    void testDocDate() {
        assertEquals("12.09.2026", format.docDate(LocalDate.of(2026, 9, 12)));
        assertEquals("01.05.2026", format.docDate(LocalDate.of(2026, 5, 1)));
        assertEquals("", format.docDate(null));
    }

    @Test
    void testNumber() {
        assertEquals("0", format.number(0));
        assertEquals("1,200", format.number(1200));
        assertEquals("182,135", format.number(182135));
        assertEquals("-26,261", format.number(-26261));
        assertEquals("999", format.number(999));
        assertEquals("", format.number(null));
    }

    @Test
    void testBudgetPoints() {
        assertEquals("-", format.budgetPoints(null));
        assertEquals("0", format.budgetPoints(0));
        assertEquals("1,200", format.budgetPoints(1200));
    }

    @Test
    void testChange() {
        assertEquals("+1,200", format.change(1200));
        assertEquals("-26,261", format.change(-26261));
        assertEquals("0", format.change(0));
    }

    @Test
    void testBanReasons() {
        assertEquals("Standard (< 1%), Pauper (5.25%)",
                format.banReasons(banChange(Map.of(MtgFormat.STANDARD, new BigDecimal("0.000"), MtgFormat.PAUPER, new BigDecimal("0.0525")), null, false)));
        assertEquals("Standard (16%), Pioneer (5%), Pauper (< 1%), Modern (12.5%), Legacy (7%), Vintage (0.2%), banned in Modern, restricted in Vintage",
                format.banReasons(banChange(Map.of(
                        MtgFormat.STANDARD, new BigDecimal("0.160"),
                        MtgFormat.PIONEER, new BigDecimal("0.050"),
                        MtgFormat.PAUPER, new BigDecimal("0.000"),
                        MtgFormat.MODERN, new BigDecimal("0.125"),
                        MtgFormat.LEGACY, new BigDecimal("0.070"),
                        MtgFormat.VINTAGE, new BigDecimal("0.002")), MtgFormat.MODERN, true)));
        assertEquals("banned in Legacy, restricted in Vintage", format.banReasons(banChange(Map.of(), MtgFormat.LEGACY, true)));
        assertEquals("", format.banReasons(banChange(Map.of(), null, false)));
        assertEquals("", format.banReasons(banChange(null, null, false)));
    }

    @Test
    void testScryfallUrl() {
        assertEquals("https://scryfall.com/search?q=!%22Ajani's%20Pridemate%22", format.scryfallUrl("Ajani's Pridemate"));
        assertEquals("https://scryfall.com/search?q=!%22Fire%20//%20Ice%22", format.scryfallUrl("Fire // Ice"));
        assertEquals("https://scryfall.com/search?q=!%22Tamiyo%EA%9E%89s%20Journal%22", format.scryfallUrl("Tamiyo꞉s Journal"));
        assertEquals("https://scryfall.com/search?q=!%22Lightning%20Bolt%22", format.scryfallUrl("Lightning Bolt"));
    }

    @Test
    void testCardCount() {
        assertEquals("0 cards", format.cardCount(null));
        assertEquals("0 cards", format.cardCount("   "));
        assertEquals("1 cards", format.cardCount("Black Lotus"));
        assertEquals("3 cards", format.cardCount("Ancestral Recall\nBlack Lotus\nTime Walk\n"));
    }

    @Test
    void testSetNames() {
        assertEquals(List.of("Secrets of Strixhaven (+ Commander Decks)", "The Hobbit", "Marvel Super Heroes"),
                format.setNames(List.of(
                        mtgSet("Secrets of Strixhaven", "SOS", List.of("Lorehold Spirit", "Prismari Artistry"), List.of("PSOS", "SOC")),
                        mtgSet("The Hobbit", "HOB", List.of(), List.of()),
                        mtgSet("Marvel Super Heroes", "MSH", null, null))));
        assertEquals(List.of(), format.setNames(List.of()));
        assertEquals(List.of(), format.setNames(null));
    }

    @Test
    void testSetCodes() {
        assertEquals(List.of("SOS", "PSOS", "SOC", "HOB", "MSH"),
                format.setCodes(List.of(
                        mtgSet("Secrets of Strixhaven", "SOS", List.of("Lorehold Spirit", "Prismari Artistry"), List.of("PSOS", "SOC")),
                        mtgSet("The Hobbit", "HOB", List.of(), List.of()),
                        mtgSet("Marvel Super Heroes", "MSH", null, null))));
        assertEquals(List.of(), format.setCodes(List.of()));
        assertEquals(List.of(), format.setCodes(null));
    }

    private static SeasonDraftReportVO.BanChangeVO banChange(Map<MtgFormat, BigDecimal> metaShares, MtgFormat bannedIn, boolean isVintageRestricted) {
        return new SeasonDraftReportVO.BanChangeVO("Lightning Bolt", 100, metaShares, bannedIn, isVintageRestricted);
    }

    private static MtgSetVO mtgSet(String name, String code, List<String> commanderDecks, List<String> childCodes) {
        return new MtgSetVO(name, code, LocalDate.of(2026, 4, 24), MtgSetType.EXPANSION, commanderDecks, childCodes, 281);
    }
}

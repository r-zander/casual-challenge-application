package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonSanityChecksVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonSanityChecksTest {

    @Test
    void testCards() {
        assertEquals("Cards", SeasonSanityChecks.cards(33000, 1457).getLabel());
        assertEquals("around 33k", SeasonSanityChecks.cards(33000, 1457).getExpectation());
        assertEquals("33,000, 1,457 of them new", SeasonSanityChecks.cards(33000, 1457).getActual());
        assertEquals("24,999, 0 of them new", SeasonSanityChecks.cards(24999, 0).getActual());
        assertEquals("45,001, 12 of them new", SeasonSanityChecks.cards(45001, 12).getActual());
        assertTrue(SeasonSanityChecks.cards(33000, 1457).isPassing());
        assertTrue(SeasonSanityChecks.cards(25000, 0).isPassing());
        assertTrue(SeasonSanityChecks.cards(45000, 0).isPassing());
        assertFalse(SeasonSanityChecks.cards(24999, 0).isPassing());
        assertFalse(SeasonSanityChecks.cards(45001, 12).isPassing());
        assertFalse(SeasonSanityChecks.cards(24999, 0).isBlocking());
    }

    @Test
    void testPricedDays() {
        LocalDate priceWindowStart = LocalDate.of(2026, 7, 12);
        LocalDate priceWindowEnd = LocalDate.of(2026, 9, 20);

        assertEquals("Priced days", SeasonSanityChecks.pricedDays(67, priceWindowStart, priceWindowEnd).getLabel());
        assertEquals("the whole window, give or take a day", SeasonSanityChecks.pricedDays(67, priceWindowStart, priceWindowEnd).getExpectation());
        assertEquals("70 of 70", SeasonSanityChecks.pricedDays(70, priceWindowStart, priceWindowEnd).getActual());
        assertEquals("67 of 70", SeasonSanityChecks.pricedDays(67, priceWindowStart, priceWindowEnd).getActual());
        assertEquals("66 of 70", SeasonSanityChecks.pricedDays(66, priceWindowStart, priceWindowEnd).getActual());
        assertTrue(SeasonSanityChecks.pricedDays(70, priceWindowStart, priceWindowEnd).isPassing());
        assertTrue(SeasonSanityChecks.pricedDays(67, priceWindowStart, priceWindowEnd).isPassing());
        assertFalse(SeasonSanityChecks.pricedDays(66, priceWindowStart, priceWindowEnd).isPassing());
        assertFalse(SeasonSanityChecks.pricedDays(66, priceWindowStart, priceWindowEnd).isBlocking());
    }

    @Test
    void testTopRows() {
        assertEquals("Top 50 rows", SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 50, 50, 50, 50), 50).getLabel());
        assertEquals("50 per format", SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 50, 50, 50, 50), 50).getActual());
        assertEquals("50 per format", SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 49, 50, 50, 50), 50).getExpectation());
        assertEquals("Pauper 49", SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 49, 50, 50, 50), 50).getActual());
        assertEquals("Pioneer 51, Pauper 49", SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 51, 49, 50, 50, 50), 50).getActual());
        assertEquals("150 per format", SeasonSanityChecks.topRows("Top 150 rows", rowCounts(150, 150, 150, 150, 150, 150), 150).getActual());
        assertEquals("Vintage 148", SeasonSanityChecks.topRows("Top 150 rows", rowCounts(150, 150, 150, 150, 150, 148), 150).getActual());
        assertTrue(SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 50, 50, 50, 50), 50).isPassing());
        assertFalse(SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 49, 50, 50, 50), 50).isPassing());
        assertTrue(SeasonSanityChecks.topRows("Top 50 rows", rowCounts(50, 50, 49, 50, 50, 50), 50).isBlocking());
    }

    @Test
    void testDuplicateMetaShareNames() {
        assertEquals("Cards counted twice", SeasonSanityChecks.duplicateMetaShareNames(List.of()).getLabel());
        assertEquals("none", SeasonSanityChecks.duplicateMetaShareNames(List.of()).getActual());
        assertEquals("none", SeasonSanityChecks.duplicateMetaShareNames(List.of()).getExpectation());
        assertEquals("Lightning Bolt, Brainstorm & Ponder",
                SeasonSanityChecks.duplicateMetaShareNames(List.of("Lightning Bolt", "Brainstorm & Ponder")).getActual());
        assertTrue(SeasonSanityChecks.duplicateMetaShareNames(List.of()).isPassing());
        assertFalse(SeasonSanityChecks.duplicateMetaShareNames(List.of("Lightning Bolt")).isPassing());
        assertTrue(SeasonSanityChecks.duplicateMetaShareNames(List.of("Lightning Bolt")).isBlocking());
    }

    @Test
    void testDuplicateMetaShareNames_withNull() {
        assertEquals("none", SeasonSanityChecks.duplicateMetaShareNames(null).getActual());
        assertTrue(SeasonSanityChecks.duplicateMetaShareNames(null).isPassing());
    }

    @Test
    void testExchangeRate() {
        assertEquals("Exchange rate", SeasonSanityChecks.exchangeRate(1.17, 1.085).getLabel());
        assertEquals("somewhere between 1 and 2.5", SeasonSanityChecks.exchangeRate(1.17, 1.085).getExpectation());
        assertEquals("1.17 USD per EUR, adjusted 1.08", SeasonSanityChecks.exchangeRate(1.17, 1.085).getActual());
        assertEquals("1.00 USD per EUR, adjusted 1.00", SeasonSanityChecks.exchangeRate(1.0, 1.005).getActual());
        assertEquals("1.60 USD per EUR, adjusted 1.30", SeasonSanityChecks.exchangeRate(1.5987249435463233, 1.2993624717731618).getActual());
        assertTrue(SeasonSanityChecks.exchangeRate(1.17, 1.085).isPassing());
        assertFalse(SeasonSanityChecks.exchangeRate(1.0, 1.0).isPassing());
        assertFalse(SeasonSanityChecks.exchangeRate(2.5, 1.75).isPassing());
        assertFalse(SeasonSanityChecks.exchangeRate(1.0, 1.0).isBlocking());
    }

    @Test
    void testOf() throws JsonProcessingException {
        String storedReport = """
                {"priceWindowStart": "2026-07-12", "priceWindowEnd": "2026-09-20", "duplicateMetaShareNames": [],
                 "counts": {"cards": 33334, "newCards": 1457, "cardsWithoutPrice": 1153, "pricedDays": 68,
                            "exchangeRate": 1.5987249435463233, "adjustedExchangeRate": 1.2993624717731618,
                            "top50Rows": {"STANDARD": 50, "PIONEER": 50, "PAUPER": 50, "MODERN": 50, "LEGACY": 50, "VINTAGE": 50},
                            "top150Rows": {"STANDARD": 150, "PIONEER": 150, "PAUPER": 150, "MODERN": 150, "LEGACY": 150, "VINTAGE": 150}}}""";
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SeasonSanityChecksVO sanityChecks = SeasonSanityChecks.of(objectMapper.readValue(storedReport, SeasonDraftReportVO.class));
        List<SeasonSanityChecksVO.SanityCheckVO> checks = sanityChecks.getChecks();

        assertEquals(7, checks.size());
        assertEquals("Cards", checks.get(0).getLabel());
        assertEquals("Priced days", checks.get(1).getLabel());
        assertEquals("Top 50 rows", checks.get(2).getLabel());
        assertEquals("Top 150 rows", checks.get(3).getLabel());
        assertEquals("Cards counted twice", checks.get(4).getLabel());
        assertEquals("Exchange rate", checks.get(5).getLabel());
        assertEquals("Cards without a price", checks.get(6).getLabel());
        assertEquals("33,334, 1,457 of them new", checks.get(0).getActual());
        assertEquals("68 of 70", checks.get(1).getActual());
        assertEquals("1.60 USD per EUR, adjusted 1.30", checks.get(5).getActual());
        assertEquals("1,153", checks.get(6).getActual());
        assertFalse(checks.get(0).isBlocking());
        assertFalse(checks.get(1).isBlocking());
        assertTrue(checks.get(2).isBlocking());
        assertTrue(checks.get(3).isBlocking());
        assertTrue(checks.get(4).isBlocking());
        assertFalse(checks.get(5).isBlocking());
        assertFalse(checks.get(6).isBlocking());
        assertTrue(checks.get(0).isPassing());
        assertTrue(checks.get(1).isPassing());
        assertTrue(checks.get(2).isPassing());
        assertTrue(checks.get(3).isPassing());
        assertTrue(checks.get(4).isPassing());
        assertTrue(checks.get(5).isPassing());
        assertTrue(checks.get(6).isPassing());
        assertFalse(sanityChecks.isCommitBlocked());
        assertEquals(List.of(), sanityChecks.getBlockingLabels());
    }

    @Test
    void testOf_withBlockingFailure() throws JsonProcessingException {
        String storedReport = """
                {"priceWindowStart": "2026-07-12", "priceWindowEnd": "2026-09-20", "duplicateMetaShareNames": ["Lightning Bolt"],
                 "counts": {"cards": 33334, "newCards": 1457, "cardsWithoutPrice": 1153, "pricedDays": 68,
                            "exchangeRate": 1.5987249435463233, "adjustedExchangeRate": 1.2993624717731618,
                            "top50Rows": {"STANDARD": 50, "PIONEER": 50, "PAUPER": 50, "MODERN": 50, "LEGACY": 50, "VINTAGE": 50},
                            "top150Rows": {"STANDARD": 150, "PIONEER": 150, "PAUPER": 150, "MODERN": 150, "LEGACY": 150, "VINTAGE": 148}}}""";
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SeasonSanityChecksVO sanityChecks = SeasonSanityChecks.of(objectMapper.readValue(storedReport, SeasonDraftReportVO.class));
        List<SeasonSanityChecksVO.SanityCheckVO> checks = sanityChecks.getChecks();

        assertEquals("Vintage 148", checks.get(3).getActual());
        assertEquals("Lightning Bolt", checks.get(4).getActual());
        assertFalse(checks.get(3).isPassing());
        assertFalse(checks.get(4).isPassing());
        assertTrue(checks.get(0).isPassing());
        assertTrue(checks.get(1).isPassing());
        assertTrue(checks.get(2).isPassing());
        assertTrue(checks.get(5).isPassing());
        assertTrue(checks.get(6).isPassing());
        assertTrue(sanityChecks.isCommitBlocked());
        assertEquals(List.of("top 150 rows", "cards counted twice"), sanityChecks.getBlockingLabels());
    }

    private static Map<MtgFormat, Integer> rowCounts(int standard, int pioneer, int pauper, int modern, int legacy, int vintage) {
        Map<MtgFormat, Integer> rowCounts = new EnumMap<>(MtgFormat.class);
        rowCounts.put(MtgFormat.STANDARD, standard);
        rowCounts.put(MtgFormat.PIONEER, pioneer);
        rowCounts.put(MtgFormat.PAUPER, pauper);
        rowCounts.put(MtgFormat.MODERN, modern);
        rowCounts.put(MtgFormat.LEGACY, legacy);
        rowCounts.put(MtgFormat.VINTAGE, vintage);

        return rowCounts;
    }
}

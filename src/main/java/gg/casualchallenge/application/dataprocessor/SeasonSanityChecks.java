package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonSanityChecksVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SeasonSanityChecks {

    public static final int EXPECTED_TOP_50_ROWS = 50;
    public static final int EXPECTED_TOP_150_ROWS = 150;
    public static final int PLAUSIBLE_CARD_COUNT_MINIMUM = 25000;
    public static final int PLAUSIBLE_CARD_COUNT_MAXIMUM = 45000;
    public static final double PLAUSIBLE_EXCHANGE_RATE_MINIMUM = 1.0;
    public static final double PLAUSIBLE_EXCHANGE_RATE_MAXIMUM = 2.5;
    public static final int ALLOWED_MISSING_PRICE_DAYS = 3; // MTGJSON drops the odd day, 68 of 70 is a normal window

    private SeasonSanityChecks() {}

    public static SeasonSanityChecksVO of(SeasonDraftReportVO report) {
        SeasonDraftReportVO.CountsVO counts = report.getCounts();

        List<SeasonSanityChecksVO.SanityCheckVO> checks = new ArrayList<>(7);
        checks.add(cards(counts.getCards(), counts.getNewCards()));
        checks.add(pricedDays(counts.getPricedDays(), report.getPriceWindowStart(), report.getPriceWindowEnd()));
        checks.add(topRows("Top 50 rows", counts.getTop50Rows(), EXPECTED_TOP_50_ROWS));
        checks.add(topRows("Top 150 rows", counts.getTop150Rows(), EXPECTED_TOP_150_ROWS));
        checks.add(duplicateMetaShareNames(report.getDuplicateMetaShareNames()));
        checks.add(exchangeRate(counts.getExchangeRate(), counts.getAdjustedExchangeRate()));
        checks.add(cardsWithoutPrice(counts.getCardsWithoutPrice()));

        return new SeasonSanityChecksVO(checks);
    }

    public static SeasonSanityChecksVO.SanityCheckVO cards(int cards, int newCards) {
        return new SeasonSanityChecksVO.SanityCheckVO(
                "Cards",
                grouped(cards) + ", " + grouped(newCards) + " of them new",
                "around 33k",
                cards >= PLAUSIBLE_CARD_COUNT_MINIMUM && cards <= PLAUSIBLE_CARD_COUNT_MAXIMUM,
                false);
    }

    public static SeasonSanityChecksVO.SanityCheckVO pricedDays(int pricedDays, LocalDate priceWindowStart, LocalDate priceWindowEnd) {
        long windowDays = ChronoUnit.DAYS.between(priceWindowStart, priceWindowEnd);

        return new SeasonSanityChecksVO.SanityCheckVO(
                "Priced days",
                pricedDays + " of " + windowDays,
                "the whole window, give or take a day",
                pricedDays >= windowDays - ALLOWED_MISSING_PRICE_DAYS,
                false);
    }

    public static SeasonSanityChecksVO.SanityCheckVO topRows(String label, Map<MtgFormat, Integer> rowCounts, int expectedRows) {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<MtgFormat, Integer> rowCount : rowCounts.entrySet()) {
            if (rowCount.getValue() != expectedRows) offenders.add(rowCount.getKey().getDisplayName() + " " + rowCount.getValue());
        }

        return new SeasonSanityChecksVO.SanityCheckVO(
                label,
                offenders.isEmpty() ? expectedRows + " per format" : String.join(", ", offenders),
                expectedRows + " per format",
                offenders.isEmpty(),
                true);
    }

    /** @param names null = the report was written before the duplicates were tracked */
    public static SeasonSanityChecksVO.SanityCheckVO duplicateMetaShareNames(List<String> names) {
        boolean isPassing = names == null || names.isEmpty();

        return new SeasonSanityChecksVO.SanityCheckVO(
                "Cards counted twice",
                isPassing ? "none" : String.join(", ", names),
                "none",
                isPassing,
                true);
    }

    public static SeasonSanityChecksVO.SanityCheckVO exchangeRate(double exchangeRate, double adjustedExchangeRate) {
        return new SeasonSanityChecksVO.SanityCheckVO(
                "Exchange rate",
                twoDecimals(exchangeRate) + " USD per EUR, adjusted " + twoDecimals(adjustedExchangeRate),
                "somewhere between 1 and 2.5",
                exchangeRate > PLAUSIBLE_EXCHANGE_RATE_MINIMUM && exchangeRate < PLAUSIBLE_EXCHANGE_RATE_MAXIMUM,
                false);
    }

    public static SeasonSanityChecksVO.SanityCheckVO cardsWithoutPrice(int cardsWithoutPrice) {
        return new SeasonSanityChecksVO.SanityCheckVO("Cards without a price", grouped(cardsWithoutPrice), "", true, false);
    }

    private static String grouped(int value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    private static String twoDecimals(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString(); // the exact double, the way toFixed(2) rounded it in the browser
    }
}

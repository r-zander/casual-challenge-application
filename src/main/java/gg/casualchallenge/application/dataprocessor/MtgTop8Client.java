package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Careful: mtgtop8 counts main deck and sideboard separately, MtgGoldfish ranks a card by both combined
// --> the two sources are not interchangeable without thought
@Service
@Slf4j
public class MtgTop8Client implements MetaGameSourceClient {

    private final static Map<MtgFormat, String> FORMAT_CODES = new EnumMap<>(MtgFormat.class);

    // Ids of the "Last 2 Months" entry in the meta drop down of https://mtgtop8.com/topcards, verified 15.09.2026
    private final static Map<MtgFormat, Integer> META_IDS = new EnumMap<>(MtgFormat.class);

    static {
        FORMAT_CODES.put(MtgFormat.STANDARD, "ST");
        FORMAT_CODES.put(MtgFormat.PIONEER, "PI");
        FORMAT_CODES.put(MtgFormat.PAUPER, "PAU");
        FORMAT_CODES.put(MtgFormat.MODERN, "MO");
        FORMAT_CODES.put(MtgFormat.LEGACY, "LE");
        FORMAT_CODES.put(MtgFormat.VINTAGE, "VI");

        META_IDS.put(MtgFormat.STANDARD, 52);
        META_IDS.put(MtgFormat.PIONEER, 193);
        META_IDS.put(MtgFormat.PAUPER, 145);
        META_IDS.put(MtgFormat.MODERN, 51);
        META_IDS.put(MtgFormat.LEGACY, 39);
        META_IDS.put(MtgFormat.VINTAGE, 82);
    }

    private final static String BASE_URL = "https://mtgtop8.com/topcards";
    private final static String MAIN_DECK = "MD";
    private final static String SIDEBOARD = "SB";
    private final static int TOP_50 = 50;
    private final static int PAGES_FOR_TOP_50 = 3;
    private final static int TOP_150 = 150;
    private final static int PAGES_FOR_TOP_150 = 8;
    private final static int DELAY_IN_MILLISECONDS = 1 * 1000;
    private final static int TIMEOUT_IN_MILLISECONDS = 30 * 1000;

    private List<Staple> fetchStaples(MtgFormat mtgFormat, int pages, int limit) {
        List<Staple> staples = merge(fetchPages(mtgFormat, MAIN_DECK, pages), fetchPages(mtgFormat, SIDEBOARD, pages), limit);
        if (staples.size() < limit) {
            log.warn("Only found {} staples for {} on mtgtop8, expected {}.", staples.size(), mtgFormat, limit);
        }

        return staples;
    }

    private List<Staple> fetchPages(MtgFormat mtgFormat, String deckSection, int pages) {
        List<Staple> staples = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            String url = BASE_URL + "?f=" + FORMAT_CODES.get(mtgFormat) + "&meta=" + META_IDS.get(mtgFormat) + "&data=1&lands=1&maindeck=" + deckSection + "&current_page=" + page;
            staples.addAll(parseStaples(fetch(url)));
        }

        return staples;
    }

    public List<Staple> parseStaples(Document document) {
        List<Staple> staples = new ArrayList<>();
        // The best card of a page is a "chosen_tr", the other 19 are "hover_tr"
        for (Element row : document.select("tr.hover_tr, tr.chosen_tr")) {
            Elements columns = row.select("td");
            if (columns.size() < 2) continue;

            String cardName = columns.get(0).text().trim();
            staples.add(new Staple(cardName, parsePercentage(columns.get(1).text(), cardName, document.location())));
        }

        return staples;
    }

    private static BigDecimal parsePercentage(String percentageText, String cardName, String url) {
        try {
            return new BigDecimal(percentageText.replace("%", "").trim()).movePointLeft(2).setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Couldn't parse percentage '" + percentageText + "' for '" + cardName + "' on '" + url + "'.", e);
        }
    }

    private static List<Staple> merge(List<Staple> mainDeckStaples, List<Staple> sideboardStaples, int limit) {
        Map<String, BigDecimal> largestShareByCardName = new LinkedHashMap<>();
        for (Staple staple : mainDeckStaples) {
            putLargerShare(largestShareByCardName, staple);
        }
        for (Staple staple : sideboardStaples) {
            putLargerShare(largestShareByCardName, staple);
        }

        List<Staple> staples = new ArrayList<>(largestShareByCardName.size());
        for (Map.Entry<String, BigDecimal> entry : largestShareByCardName.entrySet()) {
            staples.add(new Staple(entry.getKey(), entry.getValue()));
        }
        staples.sort(Comparator.comparing(Staple::getPercentageOfDecks).reversed());

        if (staples.size() > limit) {
            return new ArrayList<>(staples.subList(0, limit));
        }

        return staples;
    }

    private static void putLargerShare(Map<String, BigDecimal> largestShareByCardName, Staple staple) {
        BigDecimal knownShare = largestShareByCardName.get(staple.getCardName());
        if (knownShare == null || staple.getPercentageOfDecks().compareTo(knownShare) > 0) {
            largestShareByCardName.put(staple.getCardName(), staple.getPercentageOfDecks());
        }
    }

    private Document fetch(String url) {
        // mtgtop8 is a plain old Apache without any bot protection, but there is no reason to hammer it either
        sleep(DELAY_IN_MILLISECONDS);
        try {
            // mtgtop8 serves ISO-8859-1 and says so in its Content-Type, jsoup picks that up on its own
            return Jsoup.connect(url).timeout(TIMEOUT_IN_MILLISECONDS).get();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't connect to URL '" + url + "'.", e);
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Got interrupted while waiting for mtgtop8.", e);
        }
    }

    @Override
    public List<Staple> fetchTop50Staples(MtgFormat mtgFormat) {
        return fetchStaples(mtgFormat, PAGES_FOR_TOP_50, TOP_50);
    }

    @Override
    public List<Staple> fetchTop150Staples(MtgFormat mtgFormat) {
        return fetchStaples(mtgFormat, PAGES_FOR_TOP_150, TOP_150);
    }
}

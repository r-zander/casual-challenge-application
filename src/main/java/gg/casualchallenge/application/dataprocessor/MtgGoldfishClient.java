package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.MtgFormat;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MtgGoldfishClient implements MetaGameSourceClient {

    private final static Map<MtgFormat, String> FORMAT_PATHS = new EnumMap<>(MtgFormat.class);

    static {
        FORMAT_PATHS.put(MtgFormat.STANDARD, "standard");
        FORMAT_PATHS.put(MtgFormat.PIONEER, "pioneer");
        FORMAT_PATHS.put(MtgFormat.PAUPER, "pauper");
        FORMAT_PATHS.put(MtgFormat.MODERN, "modern");
        FORMAT_PATHS.put(MtgFormat.LEGACY, "legacy");
        FORMAT_PATHS.put(MtgFormat.VINTAGE, "vintage");
    }


    private final static int STAPLES_PER_PAGE = 50;
    private final static int MAX_ATTEMPTS = 3;
    private final static int MIN_DELAY_IN_MILLISECONDS = 1 * 1000;
    private final static int MAX_DELAY_IN_MILLISECONDS = 2 * 1000;
    private final static int BACKOFF_IN_MILLISECONDS = 5 * 1000;
    private final static int TIMEOUT_IN_MILLISECONDS = 30 * 1000;

    // MtgGoldfish hands out a _mtg_session cookie on the first request and gets touchy when the following 23 don't send it back
    private final Connection session = Jsoup.newSession()
            .timeout(TIMEOUT_IN_MILLISECONDS)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9");

    private List<Staple> fetchStaples(MtgFormat mtgFormat, PathSuffix pathSuffix)  {
        String url = "https://www.mtggoldfish.com/format-staples/" + FORMAT_PATHS.get(mtgFormat) + "/full/" + pathSuffix.toString();

        List<Staple> staples = parseStaples(fetch(url));
        if (staples.size() != STAPLES_PER_PAGE) {
            throw new IllegalStateException("Found " + staples.size() + " staples on '" + url + "', expected " + STAPLES_PER_PAGE + ".");
        }

        return staples;
    }

    public List<Staple> parseStaples(Document document) {
        // Find the table with class "table-staples"
        Element table = document.selectFirst("table.table-staples");
        if (table == null) {
            throw new IllegalStateException("Table with class 'table-staples' not found");
        }

        List<Staple> staples = new ArrayList<>(STAPLES_PER_PAGE);
        Elements rows = table.select("tr");

        for (Element row : rows) {
            Element cardLink = row.selectFirst("td.col-card a");
            if (cardLink == null) continue; // header row

            // The lands page has no "Cost" column, so counting the columns from the left doesn't work
            Elements columns = row.select("td");
            String percentageText = columns.get(columns.size() - 2).text();

            staples.add(new Staple(cardLink.text(), parsePercentage(percentageText, cardLink.text(), document.location())));
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

    private Document fetch(String url) {
        HttpStatusException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) sleep((long) (attempt - 1) * BACKOFF_IN_MILLISECONDS);

            // MtgGoldfish is not a public API, so don't hammer it
            sleep(ThreadLocalRandom.current().nextLong(MIN_DELAY_IN_MILLISECONDS, MAX_DELAY_IN_MILLISECONDS));

            try {
                return session.newRequest().url(url).get();
            } catch (HttpStatusException e) {
                // 403 is Cloudflare being unhappy about us, 429 is plain rate limiting --> both are worth another try
                if (e.getStatusCode() != 403 && e.getStatusCode() != 429) {
                    throw new RuntimeException("Couldn't connect to URL '" + url + "'.", e);
                }
                lastError = e;
            } catch (IOException e) {
                throw new RuntimeException("Couldn't connect to URL '" + url + "'.", e);
            }
        }

        throw new RuntimeException("Couldn't connect to URL '" + url + "' in " + MAX_ATTEMPTS + " attempts.", lastError);
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Got interrupted while waiting for MtgGoldfish.", e);
        }
    }

    @Override
    public List<Staple> fetchTop50Staples(MtgFormat mtgFormat) {
        return fetchStaples(mtgFormat, PathSuffix.ALL);
    }

    @Override
    public List<Staple> fetchTop150Staples(MtgFormat mtgFormat) {
        ArrayList<Staple> result = new ArrayList<>(150);
        result.addAll(fetchStaples(mtgFormat, PathSuffix.LANDS));
        result.addAll(fetchStaples(mtgFormat, PathSuffix.CREATURES));
        result.addAll(fetchStaples(mtgFormat, PathSuffix.SPELLS));
        return result;
    }

    private enum PathSuffix {
        ALL,
        LANDS,
        CREATURES,
        SPELLS;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    public static void main(String[] args) throws IOException {
        MtgGoldfishClient service = new MtgGoldfishClient();
        System.out.println("Modern Staples:");
        for (Staple staple : service.fetchTop50Staples(MtgFormat.MODERN)) {
            System.out.println(staple);
        }
    }
}

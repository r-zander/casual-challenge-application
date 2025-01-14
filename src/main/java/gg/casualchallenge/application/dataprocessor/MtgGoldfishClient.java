package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.MtgFormat;
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


    private List<Staple> fetchStaples(MtgFormat mtgFormat, PathSuffix pathSuffix)  {
        String url = "https://www.mtggoldfish.com/format-staples/" + FORMAT_PATHS.get(mtgFormat) + "/full/" + pathSuffix.toString();
        Document document = null;
        try {
            document = Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't connect to URL '" + url + "'.", e);
        }

        // Find the table with class "table-staples"
        Element table = document.selectFirst("table.table-staples");
        if (table == null) {
            throw new IllegalStateException("Table with class 'table-staples' not found");
        }

        List<Staple> staples = new ArrayList<>();
        Elements rows = table.select("tbody > tr");

        for (Element row : rows) {
            String cardName = row.selectFirst("td.col-card a").text();
            String percentageText = row.selectFirst("td.text-right:nth-of-type(4)").text();

            BigDecimal percentageOfDecks = new BigDecimal(percentageText.replace("%", "")).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);

            staples.add(new Staple(cardName, percentageOfDecks));
        }

        return staples;
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

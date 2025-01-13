package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MtgGoldfishClient {

    @Setter
    @Getter
    @AllArgsConstructor
    @ToString
    public static class Staple {
        private String cardName;
        private float percentageOfDecks;
    }

    private final static Map<MtgFormat, String> formatPaths = new EnumMap<>(MtgFormat.class);

    static {
        formatPaths.put(MtgFormat.STANDARD, "standard");
        formatPaths.put(MtgFormat.PIONEER, "pioneer");
        formatPaths.put(MtgFormat.PAUPER, "pauper");
        formatPaths.put(MtgFormat.MODERN, "modern");
        formatPaths.put(MtgFormat.LEGACY, "legacy");
        formatPaths.put(MtgFormat.VINTAGE, "vintage");
    }

    public List<Staple> fetchModernStaples() throws IOException {
        String url = "https://www.mtggoldfish.com/format-staples/modern/full/all";
        Document document = Jsoup.connect(url).get();

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

            // Convert percentage text (e.g., "38%") to a float
            float percentageOfDecks = Float.parseFloat(percentageText.replace("%", "")) / 100.0f;

            staples.add(new Staple(cardName, percentageOfDecks));
        }

        return staples;
    }

    public static void main(String[] args) throws IOException {
        MtgGoldfishClient service = new MtgGoldfishClient();
        System.out.println("Modern Staples:");
        for (Staple staple : service.fetchModernStaples()) {
            System.out.println(staple);
        }
    }
}

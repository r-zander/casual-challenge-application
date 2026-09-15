package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Staple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MtgGoldfishClientTest {

    @Test
    void testParseStaples() {
        Document document = Jsoup.parse("""
                <table class="table table-striped table-staples">
                    <tbody>
                        <tr>
                            <th class="col-card">Card</th>
                            <th class="text-end">Cost</th>
                            <th class="text-end">% of Decks</th>
                            <th class="text-end">Total Decks</th>
                        </tr>
                        <tr>
                            <td class="col-card"><a href="/price/Modern+Horizons+2/Ragavan+Nimble+Pilferer">Ragavan, Nimble Pilferer</a></td>
                            <td class="text-end">R</td>
                            <td class="text-end">53%</td>
                            <td class="text-end">1234</td>
                        </tr>
                        <tr>
                            <td class="col-card"><a href="/price/Modern+Horizons+3/Nadu+Winged+Wisdom">Nadu, Winged Wisdom</a></td>
                            <td class="text-end">1GU</td>
                            <td class="text-end">12.5%</td>
                            <td class="text-end">291</td>
                        </tr>
                    </tbody>
                </table>
                """);

        List<Staple> staples = new MtgGoldfishClient().parseStaples(document);

        assertEquals(2, staples.size());
        assertEquals("Ragavan, Nimble Pilferer", staples.get(0).getCardName());
        assertEquals(new BigDecimal("0.530"), staples.get(0).getPercentageOfDecks());
        assertEquals("Nadu, Winged Wisdom", staples.get(1).getCardName());
        assertEquals(new BigDecimal("0.125"), staples.get(1).getPercentageOfDecks());
    }

    @Test
    void testParseStaples_withLandsPage() {
        Document document = Jsoup.parse("""
                <table class="table table-striped table-staples">
                    <thead>
                        <tr>
                            <th class="col-card">Card</th>
                            <th class="text-end">% of Decks</th>
                            <th class="text-end">Total Decks</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td class="col-card"><a href="/price/Modern+Horizons+3/Flooded+Strand">Flooded Strand</a></td>
                            <td class="text-end">31%</td>
                            <td class="text-end">742</td>
                        </tr>
                        <tr>
                            <td class="col-card"><a href="/price/Tempest/Wasteland">Wasteland</a></td>
                            <td class="text-end">7%</td>
                            <td class="text-end">165</td>
                        </tr>
                    </tbody>
                </table>
                """);

        List<Staple> staples = new MtgGoldfishClient().parseStaples(document);

        assertEquals(2, staples.size());
        assertEquals("Flooded Strand", staples.get(0).getCardName());
        assertEquals(new BigDecimal("0.310"), staples.get(0).getPercentageOfDecks());
        assertEquals("Wasteland", staples.get(1).getCardName());
        assertEquals(new BigDecimal("0.070"), staples.get(1).getPercentageOfDecks());
    }
}

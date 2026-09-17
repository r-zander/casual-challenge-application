package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Staple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MtgTop8ClientTest {

    @Test
    void testParseStaples() {
        Document document = Jsoup.parse("""
                <table class="Stable">
                    <tr class="chosen_tr" id="md0">
                        <td><a href="?f=LE&amp;c=Flooded+Strand">Flooded Strand</a></td>
                        <td>36.0 %</td>
                        <td>1608</td>
                    </tr>
                    <tr class="hover_tr" id="md1">
                        <td><a href="?f=LE&amp;c=Brainstorm">Brainstorm</a></td>
                        <td>22.9 %</td>
                        <td>1024</td>
                    </tr>
                    <tr class="hover_tr" id="md2">
                        <td><a href="?f=LE&amp;c=Force+of+Will">Force of Will</a></td>
                        <td>8.0 %</td>
                        <td>358</td>
                    </tr>
                </table>
                """);

        List<Staple> staples = new MtgTop8Client().parseStaples(document);

        assertEquals(3, staples.size());
        assertEquals("Flooded Strand", staples.get(0).getCardName());
        assertEquals(new BigDecimal("0.360"), staples.get(0).getPercentageOfDecks());
        assertEquals("Brainstorm", staples.get(1).getCardName());
        assertEquals(new BigDecimal("0.229"), staples.get(1).getPercentageOfDecks());
        assertEquals("Force of Will", staples.get(2).getCardName());
        assertEquals(new BigDecimal("0.080"), staples.get(2).getPercentageOfDecks());
    }
}

package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.datamodel.LegacyMtgFormat;
import gg.casualchallenge.application.model.type.MtgFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetaSharesVOTest {

    @Test
    void testFindBan_withFrontFace() {
        MetaSharesVO metaShares = MetaSharesVO.fromStaples(
                MetaShareSource.MTGGOLDFISH,
                Map.of(MtgFormat.MODERN, List.of(new Staple("Delver of Secrets", new BigDecimal("0.120")))),
                Map.of(MtgFormat.MODERN, List.of(new Staple("Delver of Secrets", new BigDecimal("0.120"))))
        );

        assertEquals(new BigDecimal("0.120"), metaShares.findBan("Delver of Secrets").get(MtgFormat.MODERN));
        assertEquals(new BigDecimal("0.120"), metaShares.findBan("Delver of Secrets // Insectile Aberration").get(MtgFormat.MODERN));
        assertEquals(new BigDecimal("0.120"), metaShares.findExtendedBan("Delver of Secrets // Insectile Aberration").get(MtgFormat.MODERN));
        assertNull(metaShares.findBan("Insectile Aberration"));
        assertNull(metaShares.findBan("Lightning Bolt"));
    }

    @Test
    void testFindBan_withDiacritics() {
        MetaSharesVO metaShares = MetaSharesVO.fromStaples(
                MetaShareSource.MTGGOLDFISH,
                Map.of(MtgFormat.PAUPER, List.of(new Staple("Lorien Revealed", new BigDecimal("0.210")))),
                Map.of(MtgFormat.PAUPER, List.of(new Staple("Troll of Khazad-dum", new BigDecimal("0.080"))))
        );

        assertEquals(new BigDecimal("0.210"), metaShares.findBan("Lórien Revealed").get(MtgFormat.PAUPER));
        assertEquals(new BigDecimal("0.080"), metaShares.findExtendedBan("Troll of Khazad-dûm").get(MtgFormat.PAUPER));
        assertNull(metaShares.findExtendedBan("Lórien Revealed"));
    }

    @Test
    void testFromStaples() {
        Map<MtgFormat, List<Staple>> top50 = Map.of(
                MtgFormat.MODERN, List.of(new Staple("Ragavan, Nimble Pilferer", new BigDecimal("0.180"))),
                MtgFormat.LEGACY, List.of(new Staple("Ragavan, Nimble Pilferer", new BigDecimal("0.150")), new Staple("Brainstorm", new BigDecimal("0.400")), new Staple("Brainstorm", new BigDecimal("0.380")))
        );
        Map<MtgFormat, List<Staple>> top150 = Map.of(
                MtgFormat.MODERN, List.of(new Staple("Ragavan, Nimble Pilferer", new BigDecimal("0.180")), new Staple("Ancient Stirrings", new BigDecimal("0.080"))),
                MtgFormat.LEGACY, List.of(new Staple("Ragavan, Nimble Pilferer", new BigDecimal("0.150")), new Staple("Brainstorm", new BigDecimal("0.400")))
        );
        MetaSharesVO metaShares = MetaSharesVO.fromStaples(MetaShareSource.MTGGOLDFISH, top50, top150);

        assertEquals(MetaShareSource.MTGGOLDFISH, metaShares.getSource());
        assertEquals("mtggoldfish", metaShares.getSource().toString());
        assertEquals(new BigDecimal("0.180"), metaShares.findBan("Ragavan, Nimble Pilferer").get(MtgFormat.MODERN));
        assertEquals(new BigDecimal("0.150"), metaShares.findBan("Ragavan, Nimble Pilferer").get(MtgFormat.LEGACY));
        assertEquals(new BigDecimal("0.180"), metaShares.findExtendedBan("Ragavan, Nimble Pilferer").get(MtgFormat.MODERN));
        assertEquals(new BigDecimal("0.380"), metaShares.findBan("Brainstorm").get(MtgFormat.LEGACY));
        assertNull(metaShares.findBan("Ancient Stirrings"));
        assertEquals(new BigDecimal("0.080"), metaShares.findExtendedBan("Ancient Stirrings").get(MtgFormat.MODERN));
        assertEquals(1, metaShares.getTop50Rows().get(MtgFormat.MODERN).intValue());
        assertEquals(3, metaShares.getTop50Rows().get(MtgFormat.LEGACY).intValue());
        assertEquals(2, metaShares.getTop150Rows().get(MtgFormat.MODERN).intValue());
        assertNull(metaShares.getTop50Rows().get(MtgFormat.VINTAGE));
        assertEquals(List.of("Brainstorm"), metaShares.getDuplicateNames());
    }

    @Test
    void testFromBanFiles() {
        MetaSharesVO metaShares = MetaSharesVO.fromBanFiles(
                List.of(new BanDTO("Lórien Revealed", Map.of(LegacyMtgFormat.VINTAGE, new BigDecimal("0.46"), LegacyMtgFormat.PAUPER, new BigDecimal("0.21")))),
                List.of(
                        new BanDTO("Lórien Revealed", Map.of(LegacyMtgFormat.VINTAGE, new BigDecimal("0.46"), LegacyMtgFormat.PAUPER, new BigDecimal("0.21"))),
                        new BanDTO("Ancient Stirrings", Map.of(LegacyMtgFormat.MODERN, new BigDecimal("0.08")))
                )
        );

        assertEquals(MetaShareSource.FILES, metaShares.getSource());
        assertEquals(new BigDecimal("0.46"), metaShares.findBan("Lorien Revealed").get(MtgFormat.VINTAGE));
        assertEquals(new BigDecimal("0.21"), metaShares.findBan("Lórien Revealed").get(MtgFormat.PAUPER));
        assertNull(metaShares.findBan("Ancient Stirrings"));
        assertEquals(new BigDecimal("0.08"), metaShares.findExtendedBan("Ancient Stirrings").get(MtgFormat.MODERN));
        assertEquals(1, metaShares.getTop50Rows().get(MtgFormat.VINTAGE).intValue());
        assertEquals(1, metaShares.getTop150Rows().get(MtgFormat.MODERN).intValue());
        assertNull(metaShares.getTop50Rows().get(MtgFormat.MODERN));
        assertEquals(List.of(), metaShares.getDuplicateNames());
    }

    @Test
    void testFromBanFiles_withDuplicateCard() {
        MetaSharesVO metaShares = MetaSharesVO.fromBanFiles(
                List.of(
                        new BanDTO("Brainstorm", Map.of(LegacyMtgFormat.LEGACY, new BigDecimal("0.40"), LegacyMtgFormat.VINTAGE, new BigDecimal("0.35"))),
                        new BanDTO("Brainstorm", Map.of(LegacyMtgFormat.PAUPER, new BigDecimal("0.12")))
                ),
                List.of()
        );

        assertEquals(new BigDecimal("0.12"), metaShares.findBan("Brainstorm").get(MtgFormat.PAUPER));
        assertNull(metaShares.findBan("Brainstorm").get(MtgFormat.LEGACY));
        assertNull(metaShares.findBan("Brainstorm").get(MtgFormat.VINTAGE));
        assertEquals(List.of("Brainstorm"), metaShares.getDuplicateNames());
    }
}

package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.datamodel.LegacyMtgFormat;
import gg.casualchallenge.application.common.SeasonDates;
import gg.casualchallenge.application.dataprocessor.model.AssembledDraftVO;
import gg.casualchallenge.application.dataprocessor.model.CardPrices;
import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.MetaSharesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPricesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrintingsVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonSet;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.type.MtgSetType;
import gg.casualchallenge.application.model.values.MtgSetVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonPreparationServiceTest {

    private static final UUID ABRADE = UUID.fromString("f9db72dc-9a5b-48a4-a86e-7464d9a2166a");
    private static final UUID AGENT_MARIA_HILL = UUID.fromString("a2d90efe-80f2-4069-b135-a8bd00ccd2b0");
    private static final UUID ANCESTORS_CHOSEN = UUID.fromString("fc2ccab7-cab1-4463-b73d-898070136d74");
    private static final UUID ANCESTRAL_RECALL = UUID.fromString("550c74d4-1fcb-406a-b02a-639a760a4380");
    private static final UUID ANCIENT_STIRRINGS = UUID.fromString("82f18e7d-5c42-47c4-8e74-3fccc9b7b1f0");
    private static final UUID BEE_BEE_GUN = UUID.fromString("8d4a1c07-3f52-4b6e-91a8-5c0e7d2b4396");
    private static final UUID BEE_BEE_GUN_AGAIN = UUID.fromString("1c6f8b30-7e94-4d25-83a1-6b0d5e2f7c48");
    private static final UUID BLACK_LOTUS = UUID.fromString("5089ec1a-f881-4d55-af14-5d996171203b");
    private static final UUID BRAINSTORM = UUID.fromString("36cd2364-d113-47d1-b2c4-b088d9eb88dd");
    private static final UUID CHAOS_ORB = UUID.fromString("edb455f4-8dc9-4b7c-b25c-cb51b7dfbb41");
    private static final UUID DAWNING_ARCHAIC = UUID.fromString("a3e10b9b-9349-4b44-a46c-c825293dbd05");
    private static final UUID DELVER_OF_SECRETS = UUID.fromString("edd531b9-f615-4399-8c8c-1c5e18c4acbf");
    private static final UUID DINA_ESSENCE_BREWER = UUID.fromString("f61c1dc4-2f09-4b50-957f-ee656c659072");
    private static final UUID EMRAKUL_THE_EXIGENT_DOOM = UUID.fromString("4421ab7d-6d9b-4edd-b5a0-53a8ed84da6f");
    private static final UUID FRESH_FACE = UUID.fromString("bb1c9a77-4e6d-4f2a-9b3c-0a1d2e3f4a5b");
    private static final UUID FRESH_FACE_AGAIN = UUID.fromString("3f7d2b91-5a08-4c64-9e17-8d0b6c4a2f35");
    private static final UUID GLIMPSE_THE_UNTHINKABLE = UUID.fromString("552f0163-a19d-4671-888f-044fc0354875");
    private static final UUID GLIMPSE_THE_UNTHINKABLE_PLAYTEST = UUID.fromString("ab6b0048-0be6-4ff2-916e-b6a246eb765f");
    private static final UUID JOVEN_NEW = UUID.fromString("11db8545-eca6-43f5-b9e8-f302acef53a5");
    private static final UUID JOVEN_OLD = UUID.fromString("86b47725-1764-4716-993d-e4dfcea2346c");
    private static final UUID LAVA_AXE = UUID.fromString("387b6b07-a283-412d-94c3-f7f1dc76e858");
    private static final UUID LAVA_AXE_AGAIN = UUID.fromString("2a9c4d81-6f03-4e57-b214-7d5a8c1e6b90");
    private static final UUID LIGHTNING_BOLT = UUID.fromString("4457ed35-7c10-48c8-9776-456485fdf070");
    private static final UUID LORIEN_REVEALED = UUID.fromString("66f28905-c7fc-4ada-8fa0-199626d9bedb");
    private static final UUID MONUMENT_TO_ENDURANCE = UUID.fromString("e69e8de4-b521-4888-8074-17f1efe2f345");
    private static final UUID RAGAVAN = UUID.fromString("37108cd4-bbab-4ce3-9ed6-f60e8422e703");
    private static final UUID RANCOROUS_ARCHAIC = UUID.fromString("6a68fcaf-b1e8-451d-8e1c-b47db55c83cc");
    private static final UUID SOL_RING = UUID.fromString("6ad8011d-3471-4369-9d68-b264cc027487");
    private static final UUID SUNDERING_ARCHAIC = UUID.fromString("b095526e-94a4-416b-83de-d6271804ccf3");
    private static final UUID SUPERLATORIUM = UUID.fromString("8169a0a2-2e2b-4b07-bee5-6ae535458f17");
    private static final UUID TRANSCENDENT_ARCHAIC = UUID.fromString("fdd4b3a9-83ce-41bf-82e2-7808657e2c09");
    private static final UUID TRIVIA_CONTEST = UUID.fromString("cc68abf0-e826-4457-ad53-c5a068c088dd");

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 13);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 21);
    private static final LocalDate MTGJSON_DATE = LocalDate.of(2026, 9, 13);
    private static final LocalDate RELEASE_DATE = LocalDate.of(1993, 8, 5);
    private static final int WINDOW_LENGTH = 2;

    private static final SeasonDates SEASON_DATES = new SeasonDates(10, DayOfWeek.FRIDAY);

    @Test
    void testAssemble() {
        MtgJsonPrintingsVO printings = printings(
                List.of(),
                card("Brainstorm", BRAINSTORM),
                card("Sol Ring", SOL_RING),
                card("Lava, Axe", LAVA_AXE),
                card("Delver of Secrets // Delver of Secrets", DELVER_OF_SECRETS)
        );

        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        pricesByCardName.put("Brainstorm", cardPrices(50, 75));
        pricesByCardName.put("Sol Ring", cardPrices(0, 400));

        MetaSharesVO metaShares = MetaSharesVO.fromStaples(
                MetaShareSource.MTGGOLDFISH,
                Map.of(MtgFormat.LEGACY, List.of(new Staple("Brainstorm", new BigDecimal("0.400")))),
                Map.of(MtgFormat.LEGACY, List.of(new Staple("Brainstorm", new BigDecimal("0.350"))))
        );

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, List.of(), List.of(), request(MetaShareSource.MTGGOLDFISH), currentSeason(), SEASON_DATES);
        List<SeasonDraftCardVO> cards = assembledDraft.getCards();

        assertEquals(3, cards.size());

        assertEquals("Brainstorm", cards.get(0).getName());
        assertEquals(new BigDecimal("0.350"), cards.get(0).getMetaShareLegacy());
        assertNull(cards.get(0).getMetaShareStandard());
        assertNull(cards.get(0).getMetaSharePioneer());
        assertNull(cards.get(0).getMetaShareModern());
        assertNull(cards.get(0).getMetaShareVintage());
        assertNull(cards.get(0).getMetaSharePauper());

        assertEquals("Sol Ring", cards.get(1).getName());
        assertEquals(320, cards.get(1).getBudgetPoints().intValue());

        assertEquals("Lava, Axe", cards.get(2).getName());
        assertEquals("lava-axe", cards.get(2).getNormalizedName());
        assertNotNull(cards.get(2).getBudgetPoints());
        assertEquals(0, cards.get(2).getBudgetPoints().intValue());

        assertEquals(1, assembledDraft.getReport().getCounts().getPricesFixedByExchangeRateCount());
    }

    @Test
    void testAssemble_withExistingCards() {
        MtgJsonPrintingsVO printings = printings(
                List.of(),
                card("Ancestor's Chosen", ANCESTORS_CHOSEN),
                card("Joven", JOVEN_NEW),
                card("Lórien Revealed", LORIEN_REVEALED),
                card("Fresh Face", FRESH_FACE),
                card("Fresh-Face", FRESH_FACE_AGAIN),
                card("Bee Bee Gun", BEE_BEE_GUN_AGAIN),
                card("Sole Performer", null),
                card("Second Face", FRESH_FACE),
                card("Trivia Contest", SUPERLATORIUM),
                card("Lava, Axe", LAVA_AXE_AGAIN)
        );

        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        pricesByCardName.put("Ancestor's Chosen", cardPrices(6, 9));
        pricesByCardName.put("Joven", cardPrices(32, 48));
        pricesByCardName.put("Lórien Revealed", cardPrices(40, 60));
        pricesByCardName.put("Fresh Face", cardPrices(4, 6));

        List<Card> existingCards = List.of(
                existingCard("Ancestor's Chosen", "ancestors-chosen", ANCESTORS_CHOSEN),
                existingCard("Joven", "joven", JOVEN_OLD),
                existingCard("Lorien Revealed", "lorien-revealed", LORIEN_REVEALED),
                existingCard("Bee-Bee Gun", "bee-bee-gun", BEE_BEE_GUN),
                existingCard("Trivia Contest", "trivia-contest", TRIVIA_CONTEST),
                existingCard("The Superlatorium", "the-superlatorium", SUPERLATORIUM),
                existingCard("Lava, Axe", "lava,-axe", LAVA_AXE)
        );
        MetaSharesVO metaShares = MetaSharesVO.fromBanFiles(
                List.of(
                        new BanDTO("Lorien Revealed", Map.of(LegacyMtgFormat.PAUPER, new BigDecimal("0.46"))),
                        new BanDTO("Lórien Revealed", Map.of(LegacyMtgFormat.PAUPER, new BigDecimal("0.46")))
                ),
                List.of()
        );

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, existingCards, List.of(), request(MetaShareSource.FILES), currentSeason(), SEASON_DATES);
        List<SeasonDraftCardVO> cards = assembledDraft.getCards();

        assertEquals(11, cards.size());

        assertEquals("Ancestor's Chosen", cards.get(0).getName());
        assertFalse(cards.get(0).isNewCard());
        assertNull(cards.get(0).getPreviousOracleId());

        assertEquals("Joven", cards.get(1).getName());
        assertTrue(cards.get(1).isNewCard());
        assertEquals(JOVEN_OLD, cards.get(1).getPreviousOracleId());

        assertEquals("lorien-revealed", cards.get(2).getNormalizedName());
        assertFalse(cards.get(2).isNewCard());
        assertEquals(Legality.BANNED, cards.get(2).getLegality());
        assertEquals(new BigDecimal("0.460"), cards.get(2).getMetaSharePauper());

        assertTrue(cards.get(3).isNewCard());
        assertNull(cards.get(3).getSkipReason());

        assertEquals("duplicate normalized name 'fresh-face' (first: 'Fresh Face')", cards.get(4).getSkipReason());
        assertEquals("normalized name 'bee-bee-gun' already belongs to 'Bee-Bee Gun'", cards.get(5).getSkipReason());
        assertEquals("duplicate oracle id '" + FRESH_FACE + "' (first: 'Fresh Face')", cards.get(6).getSkipReason());
        assertEquals("oracle id '" + SUPERLATORIUM + "' already belongs to 'The Superlatorium'", cards.get(7).getSkipReason());

        SeasonDraftReportVO report = assembledDraft.getReport();
        assertEquals(List.of("Lórien Revealed"), report.getDuplicateMetaShareNames());
        assertEquals(7, report.getCounts().getCards());
        assertEquals(1, report.getCounts().getNewCards());
        assertEquals(5, report.getSkippedCards().size());
        assertEquals("Sole Performer", report.getSkippedCards().get(4).getName());
        assertEquals("no oracle id", report.getSkippedCards().get(4).getReason());
        assertNull(report.getSkippedCards().get(4).getOracleId());
        assertEquals(2, report.getOracleIdChanges().size());
        assertEquals("Joven", report.getOracleIdChanges().get(0).getName());
        assertEquals(JOVEN_OLD, report.getOracleIdChanges().get(0).getPreviousOracleId());
        assertEquals(JOVEN_NEW, report.getOracleIdChanges().get(0).getOracleId());
        assertEquals("Lava, Axe", report.getOracleIdChanges().get(1).getName());
        assertEquals(1, report.getRenamedCards().size());
        assertEquals("Lorien Revealed", report.getRenamedCards().get(0).getPreviousName());
        assertEquals("Lórien Revealed", report.getRenamedCards().get(0).getName());
        assertEquals(1, report.getNormalizedNameFixes().size()); // same name, other normalized name --> not a rename
        assertEquals(1, report.getCounts().getNormalizedNameFixes());
        assertEquals("Lava, Axe", report.getNormalizedNameFixes().get(0).getName());
        assertEquals("lava,-axe", report.getNormalizedNameFixes().get(0).getPreviousNormalizedName());
        assertEquals("lava-axe", report.getNormalizedNameFixes().get(0).getNormalizedName());
        assertEquals(LAVA_AXE_AGAIN, report.getNormalizedNameFixes().get(0).getOracleId());

        assertEquals("Bee-Bee Gun", cards.get(9).getName()); // no printing left, but the card table still knows it
        assertEquals(Legality.NOT_LEGAL, cards.get(9).getLegality());
        assertEquals(0, cards.get(9).getBudgetPoints().intValue());
        assertFalse(cards.get(9).isNewCard());
        assertEquals("The Superlatorium", cards.get(10).getName());
        assertEquals(2, report.getMissingCards().size());
        assertEquals("Bee-Bee Gun", report.getMissingCards().get(0).getName());
        assertEquals("no eligible printing this season, kept as not legal", report.getMissingCards().get(0).getReason());
        assertEquals("The Superlatorium", report.getMissingCards().get(1).getName());
    }

    @Test
    void testAssemble_withPlaytestCardBeforeTheRealOne() {
        MtgJsonPrintingsVO printings = printings(
                List.of(),
                new MtgJsonCard("Glimpse, the Unthinkable", GLIMPSE_THE_UNTHINKABLE_PLAYTEST, false, false, null, "MB2", LocalDate.of(2024, 11, 8)),
                card("Glimpse the Unthinkable", GLIMPSE_THE_UNTHINKABLE)
        );

        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        pricesByCardName.put("Glimpse the Unthinkable", cardPrices(300, 0));

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), MetaSharesVO.fromBanFiles(List.of(), List.of()), List.of(), List.of(), request(MetaShareSource.FILES), currentSeason(), SEASON_DATES);
        List<SeasonDraftCardVO> cards = assembledDraft.getCards();

        assertEquals(2, cards.size());
        assertEquals("Glimpse, the Unthinkable", cards.get(0).getName());
        assertEquals("duplicate normalized name 'glimpse-the-unthinkable' (first: 'Glimpse the Unthinkable')", cards.get(0).getSkipReason());
        assertEquals("Glimpse the Unthinkable", cards.get(1).getName());
        assertEquals("glimpse-the-unthinkable", cards.get(1).getNormalizedName());
        assertNull(cards.get(1).getSkipReason());
        assertEquals(1, assembledDraft.getReport().getSkippedCards().size());
        assertEquals("Glimpse, the Unthinkable", assembledDraft.getReport().getSkippedCards().get(0).getName());
    }

    @Test
    void testAssemble_report() {
        MtgJsonPrintingsVO printings = printings(
                List.of(),
                card("Brainstorm", BRAINSTORM),
                card("Ragavan, Nimble Pilferer", RAGAVAN),
                card("Abrade", ABRADE),
                card("Chaos Orb", CHAOS_ORB),
                card("Delver of Secrets // Delver of Secrets", DELVER_OF_SECRETS),
                card("Ancient Stirrings", ANCIENT_STIRRINGS),
                card("Lightning Bolt", LIGHTNING_BOLT),
                card("Ancestor's Chosen", ANCESTORS_CHOSEN)
        );

        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        pricesByCardName.put("Brainstorm", cardPrices(300, 450));
        pricesByCardName.put("Ragavan, Nimble Pilferer", cardPrices(1000, 1500));
        pricesByCardName.put("Abrade", cardPrices(50, 75));
        pricesByCardName.put("Ancient Stirrings", cardPrices(100, 150));
        pricesByCardName.put("Lightning Bolt", cardPrices(300, 0));
        pricesByCardName.put("Ancestor's Chosen", cardPrices(297, 0));

        List<Card> existingCards = List.of(
                existingCard("Brainstorm", "brainstorm", BRAINSTORM),
                existingCard("Ragavan, Nimble Pilferer", "ragavan-nimble-pilferer", RAGAVAN),
                existingCard("Abrade", "abrade", ABRADE),
                existingCard("Chaos Orb", "chaos-orb", CHAOS_ORB),
                existingCard("Delver of Secrets // Delver of Secrets", "delver-of-secrets-delver-of-secrets", DELVER_OF_SECRETS),
                existingCard("Ancient Stirrings", "ancient-stirrings", ANCIENT_STIRRINGS),
                existingCard("Lightning Bolt", "lightning-bolt", LIGHTNING_BOLT),
                existingCard("Ancestor's Chosen", "ancestors-chosen", ANCESTORS_CHOSEN),
                existingCard("Bee-Bee Gun", "bee-bee-gun", BEE_BEE_GUN)
        );
        List<CardSeasonData> previousSeasonData = List.of(
                previousSeasonData(BRAINSTORM, 100, Legality.LEGAL),
                previousSeasonData(RAGAVAN, 4000, Legality.BANNED),
                previousSeasonData(ABRADE, 50, Legality.EXTENDED),
                previousSeasonData(CHAOS_ORB, 500, Legality.LEGAL),
                previousSeasonData(DELVER_OF_SECRETS, 20, Legality.LEGAL),
                previousSeasonData(ANCIENT_STIRRINGS, 100, Legality.LEGAL),
                previousSeasonData(LIGHTNING_BOLT, 200, Legality.LEGAL),
                previousSeasonData(ANCESTORS_CHOSEN, 198, Legality.LEGAL),
                previousSeasonData(BEE_BEE_GUN, 7, Legality.LEGAL)
        );
        MetaSharesVO metaShares = MetaSharesVO.fromStaples(
                MetaShareSource.MTGGOLDFISH,
                Map.of(MtgFormat.LEGACY, List.of(new Staple("Brainstorm", new BigDecimal("0.400")))),
                Map.of(MtgFormat.MODERN, List.of(new Staple("Ancient Stirrings", new BigDecimal("0.080"))))
        );

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, existingCards, previousSeasonData, request(MetaShareSource.MTGGOLDFISH), currentSeason(), SEASON_DATES);
        SeasonDraftReportVO report = assembledDraft.getReport();

        assertEquals(21, report.getSeasonNumber());
        assertEquals("XXI", report.getRomanSeasonNumber());
        assertEquals(20, report.getPreviousSeasonNumber());
        assertEquals(MTGJSON_DATE, report.getMtgJsonDate());
        assertEquals("5.2.2+20260913", report.getMtgJsonVersion());
        assertEquals("mtggoldfish", report.getMetaSource());
        assertEquals("raoul_zander", report.getPreparedBy());
        assertNull(report.getCommittedAt());
        assertNull(report.getCommittedBy());
        assertEquals(8, report.getCounts().getCards());
        assertEquals(0, report.getCounts().getNewCards());
        assertEquals(2, report.getCounts().getCardsWithoutPrice());
        assertEquals(2, report.getCounts().getPricedDays());

        assertEquals(1, report.getNewBans().size());
        assertEquals("Brainstorm", report.getNewBans().get(0).getName());
        assertEquals(300, report.getNewBans().get(0).getBudgetPoints().intValue());
        assertEquals(new BigDecimal("0.400"), report.getNewBans().get(0).getMetaShares().get(MtgFormat.LEGACY));
        assertEquals(1, report.getUnbans().size());
        assertEquals("Ragavan, Nimble Pilferer", report.getUnbans().get(0).getName());
        assertEquals(1, report.getNewExtended().size());
        assertEquals("Ancient Stirrings", report.getNewExtended().get(0).getName());
        assertEquals(1, report.getNoLongerExtended().size());
        assertEquals("Abrade", report.getNoLongerExtended().get(0).getName());

        assertEquals(4, report.getBudgetPointChanges().size());
        assertEquals("Ragavan, Nimble Pilferer", report.getBudgetPointChanges().get(0).getName());
        assertEquals(-3000, report.getBudgetPointChanges().get(0).getChange());
        assertEquals(4000, report.getBudgetPointChanges().get(0).getPreviousBudgetPoints().intValue());
        assertEquals("Chaos Orb", report.getBudgetPointChanges().get(1).getName());
        assertEquals("Brainstorm", report.getBudgetPointChanges().get(2).getName());
        assertEquals("Lightning Bolt", report.getBudgetPointChanges().get(3).getName());
        assertEquals(3, report.getTopIncreases().size());
        assertEquals("Brainstorm", report.getTopIncreases().get(0).getName());
        assertEquals("Lightning Bolt", report.getTopIncreases().get(1).getName());
        assertEquals("Ancestor's Chosen", report.getTopIncreases().get(2).getName());
        assertEquals(99, report.getTopIncreases().get(2).getChange());
        assertEquals(3, report.getTopDecreases().size());
        assertEquals("Ragavan, Nimble Pilferer", report.getTopDecreases().get(0).getName());
        assertEquals("Chaos Orb", report.getTopDecreases().get(1).getName());
        assertEquals("Bee-Bee Gun", report.getTopDecreases().get(2).getName());

        assertEquals(2, report.getZeroBudgetPointCards().size());
        assertEquals("Chaos Orb", report.getZeroBudgetPointCards().get(0).getName());
        assertEquals(500, report.getZeroBudgetPointCards().get(0).getPreviousBudgetPoints().intValue());
        assertEquals("Bee-Bee Gun", report.getZeroBudgetPointCards().get(1).getName());

        assertEquals(2, report.getMissingCards().size());
        assertEquals("Delver of Secrets // Delver of Secrets", report.getMissingCards().get(0).getName());
        assertEquals("flip style", report.getMissingCards().get(0).getReason());
        assertEquals("Bee-Bee Gun", report.getMissingCards().get(1).getName());
        assertEquals("no eligible printing this season, kept as not legal", report.getMissingCards().get(1).getReason());
    }

    @Test
    void testAssemble_setsReleased() {
        LocalDate strixhavenRelease = LocalDate.of(2026, 4, 24);
        LocalDate marvelRelease = LocalDate.of(2026, 6, 26);
        List<MtgJsonSet> sets = List.of(
                new MtgJsonSet("Aetherdrift", "DFT", LocalDate.of(2025, 2, 14), MtgSetType.EXPANSION, null, false, List.of()),
                new MtgJsonSet("Reality Fracture", "FRA", LocalDate.of(2026, 10, 2), MtgSetType.EXPANSION, null, false, List.of()),
                new MtgJsonSet("Marvel Super Heroes", "MSH", marvelRelease, MtgSetType.EXPANSION, null, false, List.of()),
                new MtgJsonSet("Secrets of Strixhaven Promos", "PSOS", strixhavenRelease, MtgSetType.PROMO, "SOS", false, List.of()),
                new MtgJsonSet("The Zeta Set", "SLZ", LocalDate.of(2026, 9, 2), MtgSetType.BOX, null, false, List.of()),
                new MtgJsonSet("Secrets of Strixhaven Commander", "SOC", strixhavenRelease, MtgSetType.COMMANDER, "SOS", false, List.of(
                        new MtgJsonSet.MtgJsonDeck("Lorehold Spirit", "Commander Deck", strixhavenRelease),
                        new MtgJsonSet.MtgJsonDeck("Prismari Artistry", "Commander Deck", strixhavenRelease)
                )),
                new MtgJsonSet("Secrets of Strixhaven", "SOS", strixhavenRelease, MtgSetType.EXPANSION, null, false, List.of(
                        new MtgJsonSet.MtgJsonDeck("Lifegain", "Theme Deck", strixhavenRelease)
                ))
        );
        MtgJsonPrintingsVO printings = printings(
                sets,
                new MtgJsonCard("Monument to Endurance", MONUMENT_TO_ENDURANCE, true, false, null, "DFT", LocalDate.of(2025, 2, 14)),
                new MtgJsonCard("Emrakul, the Exigent Doom", EMRAKUL_THE_EXIGENT_DOOM, false, false, null, "FRA", LocalDate.of(2026, 10, 2)),
                new MtgJsonCard("Agent Maria Hill", AGENT_MARIA_HILL, true, false, null, "MSH", marvelRelease),
                new MtgJsonCard("The Dawning Archaic", DAWNING_ARCHAIC, true, false, null, "PSOS", strixhavenRelease),
                new MtgJsonCard("Dina, Essence Brewer", DINA_ESSENCE_BREWER, true, false, null, "SOC", strixhavenRelease),
                new MtgJsonCard("Sundering Archaic", SUNDERING_ARCHAIC, true, false, null, "SOS", strixhavenRelease),
                new MtgJsonCard("Rancorous Archaic", RANCOROUS_ARCHAIC, true, false, null, "SOS", strixhavenRelease),
                new MtgJsonCard("Transcendent Archaic", TRANSCENDENT_ARCHAIC, true, false, null, "SOS", strixhavenRelease)
        );

        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        pricesByCardName.put("Monument to Endurance", cardPrices(20, 0));
        pricesByCardName.put("Agent Maria Hill", cardPrices(8, 0));
        pricesByCardName.put("The Dawning Archaic", cardPrices(114, 0));
        pricesByCardName.put("Dina, Essence Brewer", cardPrices(153, 0));
        pricesByCardName.put("Sundering Archaic", cardPrices(5, 0));
        pricesByCardName.put("Rancorous Archaic", cardPrices(4, 0));
        pricesByCardName.put("Transcendent Archaic", cardPrices(8, 0));

        List<Card> existingCards = List.of(
                existingCard("Monument to Endurance", "monument-to-endurance", MONUMENT_TO_ENDURANCE),
                existingCard("The Dawning Archaic", "the-dawning-archaic", DAWNING_ARCHAIC),
                existingCard("Dina, Essence Brewer", "dina-essence-brewer", DINA_ESSENCE_BREWER),
                existingCard("Rancorous Archaic", "rancorous-archaic", RANCOROUS_ARCHAIC),
                existingCard("Transcendent Archaic", "transcendent-archaic", TRANSCENDENT_ARCHAIC)
        );
        List<CardSeasonData> previousSeasonData = List.of(
                previousSeasonData(MONUMENT_TO_ENDURANCE, 0, Legality.NOT_LEGAL),
                previousSeasonData(DAWNING_ARCHAIC, 0, Legality.NOT_LEGAL),
                previousSeasonData(DINA_ESSENCE_BREWER, 0, Legality.NOT_LEGAL),
                previousSeasonData(RANCOROUS_ARCHAIC, 0, Legality.NOT_LEGAL),
                previousSeasonData(TRANSCENDENT_ARCHAIC, 8, Legality.LEGAL)
        );

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), MetaSharesVO.fromBanFiles(List.of(), List.of()), existingCards, previousSeasonData, request(MetaShareSource.FILES), currentSeason(), SEASON_DATES);
        List<MtgSetVO> setsReleased = assembledDraft.getReport().getSetsReleased();

        assertEquals(2, setsReleased.size());
        assertEquals("SOS", setsReleased.get(0).getCode());
        assertEquals("Secrets of Strixhaven", setsReleased.get(0).getName());
        assertEquals(strixhavenRelease, setsReleased.get(0).getReleaseDate());
        assertEquals(MtgSetType.EXPANSION, setsReleased.get(0).getType());
        assertEquals(4, setsReleased.get(0).getNewCardCount());
        assertEquals(List.of("PSOS", "SOC"), setsReleased.get(0).getChildCodes());
        assertEquals(List.of("Lorehold Spirit", "Prismari Artistry"), setsReleased.get(0).getCommanderDecks());
        assertEquals("MSH", setsReleased.get(1).getCode());
        assertEquals(1, setsReleased.get(1).getNewCardCount());
        assertEquals(List.of(), setsReleased.get(1).getChildCodes());
        assertEquals(List.of(), setsReleased.get(1).getCommanderDecks());
    }

    @Test
    void testAssemble_scryfallDecks() {
        MtgJsonPrintingsVO printings = printings(
                List.of(),
                card("Brainstorm", BRAINSTORM),
                new MtgJsonCard("Black Lotus", BLACK_LOTUS, true, true, null, "LEA", RELEASE_DATE),
                new MtgJsonCard("Ancestral Recall", ANCESTRAL_RECALL, true, true, null, "LEA", RELEASE_DATE),
                new MtgJsonCard("Chaos Orb", CHAOS_ORB, true, false, MtgFormat.LEGACY, "LEA", RELEASE_DATE),
                card("Ragavan, Nimble Pilferer", RAGAVAN),
                card("Sol Ring", SOL_RING)
        );

        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        pricesByCardName.put("Brainstorm", cardPrices(300, 0));
        pricesByCardName.put("Black Lotus", cardPrices(600000, 0));
        pricesByCardName.put("Ragavan, Nimble Pilferer", cardPrices(10000, 0));
        pricesByCardName.put("Sol Ring", cardPrices(50, 0));

        List<Card> existingCards = List.of(
                existingCard("Brainstorm", "brainstorm", BRAINSTORM),
                existingCard("Black Lotus", "black-lotus", BLACK_LOTUS),
                existingCard("Ancestral Recall", "ancestral-recall", ANCESTRAL_RECALL),
                existingCard("Chaos Orb", "chaos-orb", CHAOS_ORB),
                existingCard("Ragavan, Nimble Pilferer", "ragavan-nimble-pilferer", RAGAVAN),
                existingCard("Sol Ring", "sol-ring", SOL_RING)
        );
        List<CardSeasonData> previousSeasonData = List.of(
                previousSeasonData(BRAINSTORM, 250, Legality.LEGAL),
                previousSeasonData(BLACK_LOTUS, 500000, Legality.LEGAL),
                previousSeasonData(ANCESTRAL_RECALL, 400000, Legality.BANNED),
                previousSeasonData(CHAOS_ORB, 30000, Legality.BANNED),
                previousSeasonData(RAGAVAN, 9000, Legality.BANNED),
                previousSeasonData(SOL_RING, 60, Legality.BANNED)
        );
        MetaSharesVO metaShares = MetaSharesVO.fromStaples(
                MetaShareSource.MTGGOLDFISH,
                Map.of(MtgFormat.LEGACY, List.of(new Staple("Brainstorm", new BigDecimal("0.400")))),
                Map.of()
        );

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, existingCards, previousSeasonData, request(MetaShareSource.MTGGOLDFISH), currentSeason(), SEASON_DATES);
        SeasonDraftReportVO.ScryfallDecksVO scryfallDecks = assembledDraft.getReport().getScryfallDecks();

        assertEquals("Black Lotus\nBrainstorm", scryfallDecks.getNewBans());
        assertEquals("Sol Ring", scryfallDecks.getUnbans());
        assertEquals("Brainstorm", scryfallDecks.getCurrentBans());
    }

    @Test
    void testWithDefaults() {
        SeasonPreparationRequestVO request = SeasonPreparationService.withDefaults(
                new SeasonPreparationRequestVO(START_DATE, null, null, null, null, null, "raoul_zander"),
                LocalDate.of(2026, 9, 12), 70, SEASON_DATES);

        assertEquals(START_DATE, request.getStartDate());
        assertEquals(END_DATE, request.getEndDate());
        assertEquals(LocalDate.of(2026, 7, 5), request.getPriceWindow().getStart());
        assertEquals(START_DATE, request.getPriceWindow().getEnd());
        assertEquals(MetaShareSource.MTGGOLDFISH, request.getMetaSource());
        assertNull(request.getUploadedBans());
        assertNull(request.getUploadedExtendedBans());
        assertEquals("raoul_zander", request.getPreparedBy());
    }

    @Test
    void testWithDefaults_withEverythingSet() {
        List<BanDTO> uploadedBans = List.of(new BanDTO("Lorien Revealed", Map.of(LegacyMtgFormat.PAUPER, new BigDecimal("0.46"))));

        SeasonPreparationRequestVO request = SeasonPreparationService.withDefaults(
                new SeasonPreparationRequestVO(START_DATE, END_DATE, new PriceWindowVO(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)), MetaShareSource.FILES, uploadedBans, List.of(), "raoul_zander"),
                LocalDate.of(2026, 9, 12), 70, SEASON_DATES);

        assertEquals(START_DATE, request.getStartDate());
        assertEquals(END_DATE, request.getEndDate());
        assertEquals(LocalDate.of(2026, 8, 1), request.getPriceWindow().getStart());
        assertEquals(LocalDate.of(2026, 9, 1), request.getPriceWindow().getEnd());
        assertEquals(MetaShareSource.FILES, request.getMetaSource());
        assertEquals(uploadedBans, request.getUploadedBans());
        assertEquals(List.of(), request.getUploadedExtendedBans());
    }

    @Test
    void testWithDefaults_withAnOldSeasonBefore() {
        SeasonPreparationRequestVO request = SeasonPreparationService.withDefaults(
                new SeasonPreparationRequestVO(START_DATE, null, null, null, null, null, "raoul_zander"),
                LocalDate.of(2025, 1, 1), 70, SEASON_DATES);

        assertEquals(LocalDate.of(2026, 11, 21), request.getEndDate());
    }

    @Test
    void testPruneArchive() throws IOException {
        Path archive = Files.createTempDirectory("season-archive");
        for (int seasonNumber = 17; seasonNumber <= 22; seasonNumber++) {
            Files.createDirectory(archive.resolve("season-" + seasonNumber));
        }
        Files.writeString(archive.resolve("season-17").resolve("request.json"), "{}");

        SeasonPreparationService.pruneArchive(archive, 5);

        assertFalse(Files.exists(archive.resolve("season-17")));
        assertTrue(Files.exists(archive.resolve("season-18")));
        assertTrue(Files.exists(archive.resolve("season-22")));
    }

    @Test
    void testPruneArchive_withArchivingOff() throws IOException {
        Path archive = Files.createTempDirectory("season-archive");
        Files.createDirectory(archive.resolve("season-17"));
        Files.createDirectory(archive.resolve("season-18"));

        SeasonPreparationService.pruneArchive(archive, 0);

        assertTrue(Files.exists(archive.resolve("season-17")));
        assertTrue(Files.exists(archive.resolve("season-18")));
    }

    private static MtgJsonPrintingsVO printings(List<MtgJsonSet> sets, MtgJsonCard... cards) {
        Map<String, MtgJsonCard> cardsByName = new LinkedHashMap<>();
        for (MtgJsonCard card : cards) {
            cardsByName.put(card.getName(), card);
        }

        return new MtgJsonPrintingsVO(cardsByName, Map.of(), sets, MTGJSON_DATE, "5.2.2+20260913");
    }

    private static MtgJsonCard card(String cardName, UUID oracleId) {
        return new MtgJsonCard(cardName, oracleId, true, false, null, "LEA", RELEASE_DATE);
    }

    private static CardPrices cardPrices(long eurCents, long usdCents) {
        CardPrices cardPrices = new CardPrices(WINDOW_LENGTH);
        if (eurCents > 0) cardPrices.getEur().addPrinting(new Cents[]{Cents.of(eurCents), Cents.of(eurCents)});
        if (usdCents > 0) cardPrices.getUsd().addPrinting(new Cents[]{Cents.of(usdCents), Cents.of(usdCents)});

        return cardPrices;
    }

    private static Card existingCard(String cardName, String normalizedName, UUID oracleId) {
        Card card = new Card();
        card.setOracleId(oracleId);
        card.setName(cardName);
        card.setNormalizedName(normalizedName);

        return card;
    }

    private static CardSeasonData previousSeasonData(UUID cardOracleId, int budgetPoints, Legality legality) {
        CardSeasonData cardSeasonData = new CardSeasonData();
        cardSeasonData.setCardOracleId(cardOracleId);
        cardSeasonData.setBudgetPoints(budgetPoints);
        cardSeasonData.setLegality(legality);

        return cardSeasonData;
    }

    private static SeasonPreparationRequestVO request(MetaShareSource metaSource) {
        return new SeasonPreparationRequestVO(START_DATE, END_DATE, PriceWindowVO.of(START_DATE, 70), metaSource, null, null, "raoul_zander");
    }

    private static Season currentSeason() {
        Season season = new Season();
        season.setId(20);
        season.setSeasonNumber(20);
        season.setStartDate(LocalDate.of(2026, 4, 5));
        season.setEndDate(LocalDate.of(2026, 9, 12));
        season.setUpdatedAt(LocalDateTime.of(2026, 4, 5, 10, 59));

        return season;
    }
}

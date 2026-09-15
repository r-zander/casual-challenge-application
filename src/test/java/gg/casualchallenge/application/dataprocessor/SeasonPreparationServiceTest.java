package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.datamodel.LegacyMtgFormat;
import gg.casualchallenge.application.dataprocessor.model.CardPrices;
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
import gg.casualchallenge.application.dataprocessor.model.PreparedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    private static final UUID ANCESTORS_CHOSEN = UUID.fromString("fc2ccab7-cab1-4463-b73d-898070136d74");
    private static final UUID ANCESTRAL_RECALL = UUID.fromString("550c74d4-1fcb-406a-b02a-639a760a4380");
    private static final UUID ANCIENT_STIRRINGS = UUID.fromString("82f18e7d-5c42-47c4-8e74-3fccc9b7b1f0");
    private static final UUID BEE_BEE_GUN = UUID.fromString("8d4a1c07-3f52-4b6e-91a8-5c0e7d2b4396");
    private static final UUID BEE_BEE_GUN_AGAIN = UUID.fromString("1c6f8b30-7e94-4d25-83a1-6b0d5e2f7c48");
    private static final UUID BLACK_LOTUS = UUID.fromString("5089ec1a-f881-4d55-af14-5d996171203b");
    private static final UUID BRAINSTORM = UUID.fromString("36cd2364-d113-47d1-b2c4-b088d9eb88dd");
    private static final UUID CHAOS_ORB = UUID.fromString("edb455f4-8dc9-4b7c-b25c-cb51b7dfbb41");
    private static final UUID DELVER_OF_SECRETS = UUID.fromString("edd531b9-f615-4399-8c8c-1c5e18c4acbf");
    private static final UUID FRESH_FACE = UUID.fromString("bb1c9a77-4e6d-4f2a-9b3c-0a1d2e3f4a5b");
    private static final UUID FRESH_FACE_AGAIN = UUID.fromString("3f7d2b91-5a08-4c64-9e17-8d0b6c4a2f35");
    private static final UUID JOVEN_NEW = UUID.fromString("11db8545-eca6-43f5-b9e8-f302acef53a5");
    private static final UUID JOVEN_OLD = UUID.fromString("86b47725-1764-4716-993d-e4dfcea2346c");
    private static final UUID LAVA_AXE = UUID.fromString("387b6b07-a283-412d-94c3-f7f1dc76e858");
    private static final UUID LAVA_AXE_AGAIN = UUID.fromString("2a9c4d81-6f03-4e57-b214-7d5a8c1e6b90");
    private static final UUID LIGHTNING_BOLT = UUID.fromString("4457ed35-7c10-48c8-9776-456485fdf070");
    private static final UUID LORIEN_REVEALED = UUID.fromString("66f28905-c7fc-4ada-8fa0-199626d9bedb");
    private static final UUID RAGAVAN = UUID.fromString("37108cd4-bbab-4ce3-9ed6-f60e8422e703");
    private static final UUID SOL_RING = UUID.fromString("6ad8011d-3471-4369-9d68-b264cc027487");
    private static final UUID SUPERLATORIUM = UUID.fromString("8169a0a2-2e2b-4b07-bee5-6ae535458f17");
    private static final UUID TRIVIA_CONTEST = UUID.fromString("cc68abf0-e826-4457-ad53-c5a068c088dd");

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 13);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 21);
    private static final LocalDate MTGJSON_DATE = LocalDate.of(2026, 9, 13);
    private static final LocalDate RELEASE_DATE = LocalDate.of(1993, 8, 5);
    private static final int WINDOW_LENGTH = 2;

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
        pricesByCardName.put("Brainstorm", cardPrices(0.5, 0.75));
        pricesByCardName.put("Sol Ring", cardPrices(0, 4.0));

        MetaSharesVO metaShares = MetaSharesVO.fromStaples(
                MetaShareSource.MTGGOLDFISH,
                Map.of(MtgFormat.LEGACY, List.of(new Staple("Brainstorm", new BigDecimal("0.400")))),
                Map.of(MtgFormat.LEGACY, List.of(new Staple("Brainstorm", new BigDecimal("0.350"))))
        );

        PreparedSeasonVO preparedSeason = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, List.of(), List.of(), request(MetaShareSource.MTGGOLDFISH), currentSeason());
        List<SeasonDraftCardVO> cards = preparedSeason.getCards();

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
        assertEquals("lava,-axe", cards.get(2).getNormalizedName());
        assertNotNull(cards.get(2).getBudgetPoints());
        assertEquals(0, cards.get(2).getBudgetPoints().intValue());

        assertEquals(1, preparedSeason.getReport().getCounts().getPricesFixedByExchangeRate());
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
        pricesByCardName.put("Ancestor's Chosen", cardPrices(0.06, 0.09));
        pricesByCardName.put("Joven", cardPrices(0.32, 0.48));
        pricesByCardName.put("Lórien Revealed", cardPrices(0.4, 0.6));
        pricesByCardName.put("Fresh Face", cardPrices(0.04, 0.06));

        List<Card> existingCards = List.of(
                existingCard("Ancestor's Chosen", "ancestors-chosen", ANCESTORS_CHOSEN),
                existingCard("Joven", "joven", JOVEN_OLD),
                existingCard("Lorien Revealed", "lorien-revealed", LORIEN_REVEALED),
                existingCard("Bee-Bee Gun", "bee-bee-gun", BEE_BEE_GUN),
                existingCard("Trivia Contest", "trivia-contest", TRIVIA_CONTEST),
                existingCard("The Superlatorium", "the-superlatorium", SUPERLATORIUM),
                existingCard("Lava, Axe", "lava-axe", LAVA_AXE)
        );
        MetaSharesVO metaShares = MetaSharesVO.fromBanFiles(
                List.of(new BanDTO("Lorien Revealed", Map.of(LegacyMtgFormat.PAUPER, new BigDecimal("0.46")))),
                List.of()
        );

        PreparedSeasonVO preparedSeason = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, existingCards, List.of(), request(MetaShareSource.FILES), currentSeason());
        List<SeasonDraftCardVO> cards = preparedSeason.getCards();

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

        SeasonDraftReportVO report = preparedSeason.getReport();
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
        assertEquals("lava-axe", report.getNormalizedNameFixes().get(0).getPreviousNormalizedName());
        assertEquals("lava,-axe", report.getNormalizedNameFixes().get(0).getNormalizedName());
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
    void testAssemble_report() {
        List<MtgJsonSet> sets = List.of(
                new MtgJsonSet("Aetherdrift", "DFT", LocalDate.of(2026, 2, 14), "expansion", null, false, List.of()),
                new MtgJsonSet("Edge of Eternities", "EOE", LocalDate.of(2026, 10, 2), "expansion", null, false, List.of(
                        new MtgJsonSet.MtgJsonDeck("Cosmic Conquest", "Commander Deck", LocalDate.of(2026, 10, 2)),
                        new MtgJsonSet.MtgJsonDeck("Edge of Eternities", "Draft Deck", LocalDate.of(2026, 10, 2))
                )),
                new MtgJsonSet("Secret Lair Drop", "SLD", LocalDate.of(2026, 10, 10), "box", null, false, List.of()),
                new MtgJsonSet("Alchemy: Edge of Eternities", "YEOE", LocalDate.of(2026, 10, 15), "expansion", "EOE", true, List.of())
        );
        MtgJsonPrintingsVO printings = printings(
                sets,
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
        pricesByCardName.put("Brainstorm", cardPrices(3.0, 4.5));
        pricesByCardName.put("Ragavan, Nimble Pilferer", cardPrices(10.0, 15.0));
        pricesByCardName.put("Abrade", cardPrices(0.5, 0.75));
        pricesByCardName.put("Ancient Stirrings", cardPrices(1.0, 1.5));
        pricesByCardName.put("Lightning Bolt", cardPrices(3.0, 0));
        pricesByCardName.put("Ancestor's Chosen", cardPrices(2.97, 0));

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

        PreparedSeasonVO preparedSeason = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, existingCards, previousSeasonData, request(MetaShareSource.MTGGOLDFISH), currentSeason());
        SeasonDraftReportVO report = preparedSeason.getReport();

        assertEquals(21, report.getSeasonNumber());
        assertEquals("XXI", report.getRomanSeasonNumber());
        assertEquals(20, report.getPreviousSeasonNumber());
        assertEquals(MTGJSON_DATE, report.getMtgJsonDate());
        assertEquals("5.2.2+20260913", report.getMtgJsonVersion());
        assertEquals("mtggoldfish", report.getMetaSource());
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

        assertEquals(1, report.getSetsReleased().size());
        assertEquals("Edge of Eternities", report.getSetsReleased().get(0).getName());
        assertEquals("EOE", report.getSetsReleased().get(0).getCode());
        assertEquals("expansion", report.getSetsReleased().get(0).getType());
        assertEquals(List.of("Cosmic Conquest"), report.getSetsReleased().get(0).getCommanderDecks());
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
        pricesByCardName.put("Brainstorm", cardPrices(3.0, 0));
        pricesByCardName.put("Black Lotus", cardPrices(6000.0, 0));
        pricesByCardName.put("Ragavan, Nimble Pilferer", cardPrices(100.0, 0));
        pricesByCardName.put("Sol Ring", cardPrices(0.5, 0));

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

        PreparedSeasonVO preparedSeason = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, WINDOW_LENGTH), metaShares, existingCards, previousSeasonData, request(MetaShareSource.MTGGOLDFISH), currentSeason());
        SeasonDraftReportVO.ScryfallDecksVO scryfallDecks = preparedSeason.getReport().getScryfallDecks();

        assertEquals("Black Lotus\nBrainstorm", scryfallDecks.getNewBans());
        assertEquals("Sol Ring", scryfallDecks.getUnbans());
        assertEquals("Brainstorm", scryfallDecks.getCurrentBans());
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

    private static CardPrices cardPrices(double eurPrice, double usdPrice) {
        CardPrices cardPrices = new CardPrices(WINDOW_LENGTH);
        if (eurPrice > 0) cardPrices.getEur().addPrinting(new double[]{eurPrice, eurPrice});
        if (usdPrice > 0) cardPrices.getUsd().addPrinting(new double[]{usdPrice, usdPrice});

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
        return new SeasonPreparationRequestVO(START_DATE, END_DATE, PriceWindowVO.of(START_DATE, 70), metaSource, null, null);
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

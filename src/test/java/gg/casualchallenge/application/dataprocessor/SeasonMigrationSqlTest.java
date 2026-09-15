package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonMigrationSqlTest {

    private static final UUID ANCESTORS_CHOSEN = UUID.fromString("fc2ccab7-cab1-4463-b73d-898070136d74");
    private static final UUID ANCESTRAL_RECALL = UUID.fromString("550c74d4-1fcb-406a-b02a-639a760a4380");
    private static final UUID ANGEL_OF_MERCY = UUID.fromString("a2daaf32-dbfe-4618-892e-0da24f63a44a");
    private static final UUID BLACK_LOTUS = UUID.fromString("5f8287b2-5b4c-4b31-8c59-3e2a1d7f9c6b");
    private static final UUID BRAINSTORM = UUID.fromString("4b2c9e08-1d56-4a37-bf10-7c8e3d5a6209");
    private static final UUID JOVEN_NEW = UUID.fromString("2d8e4f10-3b6c-4d5e-9a7f-8b0c1d2e3f40");
    private static final UUID JOVEN_OLD = UUID.fromString("7a4b1c0e-9f2d-4a3b-8c7d-1e5f6a2b3c4d");
    private static final UUID SOL_RING = UUID.fromString("6ad8011d-3471-4369-9d68-b264cc027487");

    private static final LocalDateTime PREPARED_AT = LocalDateTime.of(2026, 9, 13, 18, 21, 10);

    @Test
    void testAddSeason() {
        String sql = SeasonMigrationSql.addSeason("raoul_zander", "20260913_2021_00_add_season_21.sql", draft(), List.of(), List.of());

        assertEquals("""
                -- liquibase formatted sql

                -- changeset raoul_zander:20260913_2021_00_add_season_21.sql
                UPDATE public.season
                    SET end_date = '2026-09-12',
                        updated_at = now()
                    WHERE id = 20;
                INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
                VALUES
                    (21, 21, '2026-09-13', '2026-11-21', now())
                ON CONFLICT (id) DO NOTHING;
                """, sql);
    }

    @Test
    void testAddSeason_withChangedCards() {
        String sql = SeasonMigrationSql.addSeason(
                "raoul_zander",
                "20260913_2021_00_add_season_21.sql",
                draft(),
                List.of(new SeasonDraftReportVO.OracleIdChangeVO("Joven and Chandler", JOVEN_OLD, JOVEN_NEW, "ATQ")),
                List.of(new SeasonDraftReportVO.RenamedCardVO(JOVEN_NEW, "Joven", "Joven and Chandler", "joven-and-chandler")));

        assertEquals("""
                -- liquibase formatted sql

                -- changeset raoul_zander:20260913_2021_00_add_season_21.sql
                UPDATE public.season
                    SET end_date = '2026-09-12',
                        updated_at = now()
                    WHERE id = 20;
                INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
                VALUES
                    (21, 21, '2026-09-13', '2026-11-21', now())
                ON CONFLICT (id) DO NOTHING;

                -- card_season_data references card.oracle_id without ON UPDATE CASCADE --> both updates have to be one statement
                WITH remapped_card AS (UPDATE public.card SET oracle_id = '2d8e4f10-3b6c-4d5e-9a7f-8b0c1d2e3f40'::uuid WHERE oracle_id = '7a4b1c0e-9f2d-4a3b-8c7d-1e5f6a2b3c4d'::uuid) UPDATE public.card_season_data SET card_oracle_id = '2d8e4f10-3b6c-4d5e-9a7f-8b0c1d2e3f40'::uuid WHERE card_oracle_id = '7a4b1c0e-9f2d-4a3b-8c7d-1e5f6a2b3c4d'::uuid; -- Joven and Chandler

                UPDATE public.card SET name = 'Joven and Chandler', normalized_name = 'joven-and-chandler' WHERE oracle_id = '2d8e4f10-3b6c-4d5e-9a7f-8b0c1d2e3f40'::uuid; -- was 'Joven'
                """, sql);
    }

    @Test
    void testInsertCards() {
        List<SeasonDraftCardVO> cards = List.of(
                newCard(ANCESTORS_CHOSEN, "Ancestor's Chosen", "ancestors-chosen", null),
                newCard(ANGEL_OF_MERCY, "Angel of Mercy", "angel-of-mercy", null),
                newCard(BLACK_LOTUS, "Black Lotus", "black-lotus", "duplicate normalized name"),
                knownCard(SOL_RING, "Sol Ring", "sol-ring")
        );

        String sql = SeasonMigrationSql.insertCards(cards, PREPARED_AT);

        assertEquals("""
                INSERT INTO public.card (oracle_id, "name", normalized_name, added_at)
                VALUES
                    \t('fc2ccab7-cab1-4463-b73d-898070136d74'::uuid, 'Ancestor''s Chosen', 'ancestors-chosen', '2026-09-13T18:21:10+00:00'),
                \t('a2daaf32-dbfe-4618-892e-0da24f63a44a'::uuid, 'Angel of Mercy', 'angel-of-mercy', '2026-09-13T18:21:10+00:00')
                ON CONFLICT (oracle_id) DO NOTHING;
                """, sql);
    }

    /** The python flushed on the index over all cards, skipped ones included, so the first block carries 1001 rows. */
    @Test
    void testInsertCards_withChunking() {
        String sql = SeasonMigrationSql.insertCards(numberedCards(1001), PREPARED_AT);

        assertFalse(sql.contains("ON CONFLICT (oracle_id) DO NOTHING;\nINSERT INTO public.card "));
        assertTrue(sql.endsWith(", 'Card 1000', 'card-1000', '2026-09-13T18:21:10+00:00')\nON CONFLICT (oracle_id) DO NOTHING;\n"));

        sql = SeasonMigrationSql.insertCards(numberedCards(1002), PREPARED_AT);

        assertTrue(sql.contains(", 'Card 1000', 'card-1000', '2026-09-13T18:21:10+00:00')\n"
                + "ON CONFLICT (oracle_id) DO NOTHING;\n"
                + "INSERT INTO public.card (oracle_id, \"name\", normalized_name, added_at)\n"
                + "VALUES\n    \t("));
        assertTrue(sql.endsWith(", 'Card 1001', 'card-1001', '2026-09-13T18:21:10+00:00')\nON CONFLICT (oracle_id) DO NOTHING;\n"));
    }

    @Test
    void testInsertCardSeasonData() {
        List<SeasonDraftCardVO> cards = List.of(
                new SeasonDraftCardVO(SOL_RING, null, "Sol Ring", "sol-ring", 12, Legality.LEGAL,
                        null, null, null, null, null, null, null, false, false, null),
                new SeasonDraftCardVO(ANCESTRAL_RECALL, null, "Ancestral Recall", "ancestral-recall", 65924, Legality.BANNED,
                        null, null, null, null, new BigDecimal("0.680"), null, MtgFormat.LEGACY, true, false, null),
                new SeasonDraftCardVO(BRAINSTORM, null, "Brainstorm", "brainstorm", 300, Legality.BANNED,
                        new BigDecimal("0.050"), null, null, new BigDecimal("0.400"), null, null, null, false, false, null),
                new SeasonDraftCardVO(BLACK_LOTUS, null, "Black Lotus", "black-lotus", 599200, Legality.NOT_LEGAL,
                        null, null, null, null, null, null, null, true, false, "duplicate normalized name")
        );

        String sql = SeasonMigrationSql.insertCardSeasonData(21, cards);

        assertEquals("""
                INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, meta_share_standard, meta_share_pioneer, meta_share_modern, meta_share_legacy, meta_share_vintage, meta_share_pauper, banned_in, vintage_restricted)
                VALUES
                    \t(21, '550c74d4-1fcb-406a-b02a-639a760a4380'::uuid, 65924, 'banned'::legality, NULL, NULL, NULL, NULL, 0.680, NULL, 'legacy'::mtg_format, TRUE),
                \t(21, '4b2c9e08-1d56-4a37-bf10-7c8e3d5a6209'::uuid, 300, 'banned'::legality, 0.050, NULL, NULL, 0.400, NULL, NULL, NULL, FALSE),
                \t(21, '6ad8011d-3471-4369-9d68-b264cc027487'::uuid, 12, 'legal'::legality, NULL, NULL, NULL, NULL, NULL, NULL, NULL, FALSE)
                ON CONFLICT (season_id, card_oracle_id) DO UPDATE SET
                    budget_points = EXCLUDED.budget_points,
                    legality = EXCLUDED.legality,
                    meta_share_standard = EXCLUDED.meta_share_standard,
                    meta_share_pioneer = EXCLUDED.meta_share_pioneer,
                    meta_share_modern = EXCLUDED.meta_share_modern,
                    meta_share_legacy = EXCLUDED.meta_share_legacy,
                    meta_share_vintage = EXCLUDED.meta_share_vintage,
                    meta_share_pauper = EXCLUDED.meta_share_pauper,
                    banned_in = EXCLUDED.banned_in,
                    vintage_restricted = EXCLUDED.vintage_restricted;
                """, sql);
    }

    private static SeasonDraftVO draft() {
        return new SeasonDraftVO(
                7,
                21,
                LocalDate.of(2026, 9, 13),
                LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 7, 5),
                LocalDate.of(2026, 9, 13),
                20,
                LocalDateTime.of(2026, 4, 5, 10, 59),
                "2026-09-13",
                "mtggoldfish",
                PREPARED_AT,
                null,
                "{}");
    }

    private static List<SeasonDraftCardVO> numberedCards(int cardCount) {
        List<SeasonDraftCardVO> cards = new ArrayList<>(cardCount);
        for (int cardNumber = 0; cardNumber < cardCount; cardNumber++) {
            cards.add(newCard(new UUID(0, cardNumber), "Card " + cardNumber, "card-" + cardNumber, null));
        }

        return cards;
    }

    private static SeasonDraftCardVO newCard(UUID oracleId, String name, String normalizedName, String skipReason) {
        return new SeasonDraftCardVO(oracleId, null, name, normalizedName, 10, Legality.LEGAL,
                null, null, null, null, null, null, null, false, true, skipReason);
    }

    private static SeasonDraftCardVO knownCard(UUID oracleId, String name, String normalizedName) {
        return new SeasonDraftCardVO(oracleId, null, name, normalizedName, 10, Legality.LEGAL,
                null, null, null, null, null, null, null, false, false, null);
    }
}

package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.CommittedSeasonCountsVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.model.values.SeasonRemovalCountsVO;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonDraftRepositoryTest {

    private static final UUID ANCESTORS_CHOSEN = UUID.fromString("fc2ccab7-cab1-4463-b73d-898070136d74");
    private static final UUID JOVEN_OLD = UUID.fromString("86b47725-1764-4716-993d-e4dfcea2346c");
    private static final UUID JOVEN_NEW = UUID.fromString("11db8545-eca6-43f5-b9e8-f302acef53a5");
    private static final UUID BLACK_LOTUS = UUID.fromString("5089ec1a-f881-4d55-af14-5d996171203b");
    private static final UUID FRESH_FACE = UUID.fromString("bb1c9a77-4e6d-4f2a-9b3c-0a1d2e3f4a5b");
    private static final UUID OTHER_JOVEN = UUID.fromString("0f6a2c85-4d71-4e93-b508-6c3d9a1f7e24");

    private static final UUID GONE_CARD = UUID.fromString("e91d3b52-8a7c-4f16-b2d9-0c4e5a6b7d38");

    private static final LocalDate SEASON_20_END_DATE = LocalDate.of(2026, 6, 21);
    private static final LocalDateTime SEASON_20_UPDATED_AT = LocalDateTime.of(2026, 4, 6, 10, 59, 0);
    private static final LocalDateTime SEASON_20_ADDED_AT = LocalDateTime.of(2026, 4, 6, 8, 59, 1);
    private static final LocalDateTime PREPARED_AT = LocalDateTime.of(2026, 6, 7, 18, 30, 0);
    private static final LocalDateTime COMMITTED_AT = LocalDateTime.of(2026, 6, 8, 9, 15, 0);
    private static final LocalDateTime REMOVED_AT = LocalDateTime.of(2026, 6, 8, 11, 40, 0);

    private static final List<SeasonDraftReportVO.OracleIdChangeVO> ORACLE_ID_CHANGES =
            List.of(new SeasonDraftReportVO.OracleIdChangeVO("Joven and Chandler", JOVEN_OLD, JOVEN_NEW, "ATQ"));
    private static final List<SeasonDraftReportVO.RenamedCardVO> RENAMED_CARDS =
            List.of(new SeasonDraftReportVO.RenamedCardVO(JOVEN_NEW, "Joven", "joven", "Joven and Chandler", "joven-and-chandler"));

    private static EmbeddedPostgres embeddedPostgres;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static SeasonDraftRepository seasonDraftRepository;

    @BeforeAll
    static void startDatabase() throws Exception {
        embeddedPostgres = EmbeddedPostgres.start();
        DataSource dataSource = embeddedPostgres.getPostgresDatabase();

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/changelog-master.xml");
        liquibase.afterPropertiesSet();

        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        seasonDraftRepository = new SeasonDraftRepository(jdbcTemplate);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        embeddedPostgres.close();
    }

    @Test
    void testCommit() {
        seedPreviousSeason();
        int draftId = replaceDraft();

        CommittedSeasonCountsVO counts = commitDraftInTransaction(draftId);

        assertEquals(1, counts.getRemappedCards());
        assertEquals(1, counts.getUpdatedCardNames());
        assertEquals(1, counts.getInsertedCards());
        assertEquals(3, counts.getUpsertedCardSeasonData());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE id = 21 AND season_number = 21", Integer.class));
        assertEquals(LocalDate.of(2026, 6, 8), jdbcTemplate.queryForObject("SELECT start_date FROM public.season WHERE id = 21", LocalDate.class));
        assertEquals(LocalDate.of(2026, 8, 16), jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 21", LocalDate.class));
        assertEquals(LocalDate.of(2026, 6, 7), jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class));
        assertTrue(jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class).isAfter(SEASON_20_UPDATED_AT));
        assertEquals(22L, jdbcTemplate.queryForObject("SELECT nextval('season_id_seq')", Long.class));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card", Integer.class));
        assertEquals(PREPARED_AT, jdbcTemplate.queryForObject("SELECT added_at FROM public.card WHERE oracle_id = ?", LocalDateTime.class, FRESH_FACE));
        assertEquals("Joven and Chandler", jdbcTemplate.queryForObject("SELECT name FROM public.card WHERE oracle_id = ?", String.class, JOVEN_NEW));
        assertEquals("joven-and-chandler", jdbcTemplate.queryForObject("SELECT normalized_name FROM public.card WHERE oracle_id = ?", String.class, JOVEN_NEW));
        assertEquals("Ancestor's Chosen", jdbcTemplate.queryForObject("SELECT name FROM public.card WHERE oracle_id = ?", String.class, ANCESTORS_CHOSEN));
        assertEquals(SEASON_20_ADDED_AT, jdbcTemplate.queryForObject("SELECT added_at FROM public.card WHERE oracle_id = ?", LocalDateTime.class, JOVEN_NEW)); // the remapped card is flagged as new as well --> the insert has to run into the conflict
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card WHERE oracle_id = ?", Integer.class, JOVEN_OLD));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data WHERE card_oracle_id = ?", Integer.class, JOVEN_NEW));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data WHERE card_oracle_id = ?", Integer.class, JOVEN_OLD));
        assertEquals(6, jdbcTemplate.queryForObject("SELECT budget_points FROM public.card_season_data WHERE season_id = 20 AND card_oracle_id = ?", Integer.class, ANCESTORS_CHOSEN));
        assertEquals(12, jdbcTemplate.queryForObject("SELECT budget_points FROM public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?", Integer.class, ANCESTORS_CHOSEN));
        assertEquals("extended", jdbcTemplate.queryForObject("SELECT legality::text FROM public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?", String.class, ANCESTORS_CHOSEN));
        assertEquals("legacy", jdbcTemplate.queryForObject("SELECT banned_in::text FROM public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?", String.class, JOVEN_NEW));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?", Integer.class, BLACK_LOTUS));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data WHERE season_id = 21", Integer.class));
        assertNotNull(seasonDraftRepository.findDraft().getCommittedAt());
        assertEquals("janik_nissen", jdbcTemplate.queryForObject("SELECT committed_by FROM public.season_draft WHERE id = ?", String.class, draftId));
        assertEquals("janik_nissen", seasonDraftRepository.findDraft().getCommittedBy());
        assertEquals("raoul_zander", seasonDraftRepository.findDraft().getPreparedBy());
        assertNull(seasonDraftRepository.findUncommittedDraft());
    }

    @Test
    void testCommit_withCommittedDraft() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> commitDraftInTransaction(draftId));
        assertEquals("Draft for season 21 was already committed.", exception.getMessage());
    }

    @Test
    void testCommit_withStaleDraft() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        jdbcTemplate.update("UPDATE public.season SET updated_at = now() WHERE id = 20");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> commitDraftInTransaction(draftId));
        assertEquals("Draft for season 21 is stale, season 20 changed since the draft was prepared.", exception.getMessage());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE season_number = 21", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data", Integer.class));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testCommit_withFailingRemap() {
        seedPreviousSeason();
        jdbcTemplate.update("INSERT INTO public.card (oracle_id, name, normalized_name, added_at) VALUES (?, 'Unrelated Card', 'unrelated-card', ?)", JOVEN_NEW, SEASON_20_ADDED_AT);
        int draftId = replaceDraft();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> commitDraftInTransaction(draftId));
        assertEquals("Card 'Joven and Chandler' is remapped to oracle id '" + JOVEN_NEW + "', which already belongs to 'Unrelated Card'.", exception.getMessage());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE season_number = 21", Integer.class));
        assertEquals(SEASON_20_END_DATE, jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class));
        assertEquals(SEASON_20_UPDATED_AT, jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card", Integer.class));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testCommit_withFailingRename() {
        seedPreviousSeason();
        jdbcTemplate.update("INSERT INTO public.card (oracle_id, name, normalized_name, added_at) VALUES (?, 'Joven & Chandler', 'joven-and-chandler', ?)", OTHER_JOVEN, SEASON_20_ADDED_AT);
        int draftId = replaceDraft();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> commitDraftInTransaction(draftId));
        assertEquals("Card 'Joven' would be renamed to 'Joven and Chandler', which already belongs to 'Joven & Chandler'.", exception.getMessage());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE season_number = 21", Integer.class));
        assertEquals(SEASON_20_UPDATED_AT, jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class));
        assertEquals("Joven", jdbcTemplate.queryForObject("SELECT name FROM public.card WHERE oracle_id = ?", String.class, JOVEN_OLD));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testCommit_withUnknownCard() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        jdbcTemplate.update("INSERT INTO public.season_draft_card (season_draft_id, oracle_id, name, normalized_name, budget_points, legality, vintage_restricted, is_new_card)" +
                " VALUES (?, ?, 'Gone Card', 'gone-card', 4, 'legal'::legality, FALSE, FALSE)", draftId, GONE_CARD);

        assertThrows(DataIntegrityViolationException.class, () -> commitDraftInTransaction(draftId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE season_number = 21", Integer.class));
        assertEquals(SEASON_20_END_DATE, jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class));
        assertEquals(SEASON_20_UPDATED_AT, jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data", Integer.class));
        assertEquals("Joven", jdbcTemplate.queryForObject("SELECT name FROM public.card WHERE oracle_id = ?", String.class, JOVEN_OLD));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testReplace() {
        seedPreviousSeason();
        replaceDraft();
        int draftId = replaceDraft();

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft", Integer.class));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft_card", Integer.class));
        assertEquals(draftId, seasonDraftRepository.findDraft().getId());
        assertEquals(21, seasonDraftRepository.findDraft().getSeasonNumber());
        assertEquals(SEASON_20_UPDATED_AT, seasonDraftRepository.findDraft().getPreviousSeasonUpdatedAt());

        List<SeasonDraftCardVO> cards = seasonDraftRepository.findDraftCards(draftId);
        assertEquals(4, cards.size());
        assertEquals("Ancestor's Chosen", cards.get(0).getName());
        assertEquals("Joven and Chandler", cards.get(1).getName());
        assertEquals("Black Lotus", cards.get(2).getName());
        assertEquals("Fresh Face", cards.get(3).getName());
        assertEquals(Legality.EXTENDED, cards.get(0).getLegality());
        assertEquals(new BigDecimal("0.120"), cards.get(0).getMetaShareModern());
        assertEquals(MtgFormat.LEGACY, cards.get(1).getBannedIn());
        assertEquals(JOVEN_OLD, cards.get(1).getPreviousOracleId());
        assertEquals("duplicate normalized name", cards.get(2).getSkipReason());
        assertTrue(cards.get(3).isNewCard());
    }

    @Test
    void testReplace_withCommittedDraft() {
        seedPreviousSeason();
        int committedDraftId = replaceDraft();
        commitDraftInTransaction(committedDraftId);
        int draftId = replaceDraft();

        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft", Integer.class));
        assertEquals(draftId, seasonDraftRepository.findDraft().getId());
        assertEquals(draftId, seasonDraftRepository.findUncommittedDraft().getId());
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft_card WHERE season_draft_id = ?", Integer.class, committedDraftId)); // newest committed --> its files can still be downloaded

        seasonDraftRepository.discard();

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft", Integer.class));
        assertEquals(committedDraftId, seasonDraftRepository.findDraft().getId());
        assertNull(seasonDraftRepository.findUncommittedDraft());
    }

    @Test
    void testReplace_withOlderCommittedDrafts() {
        seedPreviousSeason();
        int olderDraftId = replaceDraft();
        commitDraftInTransaction(olderDraftId);
        jdbcTemplate.update("UPDATE public.season_draft SET season_number = 22 WHERE id = ?", olderDraftId); // a second committed draft on top of the first
        jdbcTemplate.update("INSERT INTO public.season (id, season_number, start_date, end_date, updated_at) VALUES (22, 22, '2026-08-17', '2026-10-25', ?)", SEASON_20_UPDATED_AT);
        int committedDraftId = replaceDraft();
        jdbcTemplate.update("UPDATE public.season_draft SET committed_at = ? WHERE id = ?", COMMITTED_AT, committedDraftId);
        replaceDraft();

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft_card WHERE season_draft_id = ?", Integer.class, olderDraftId));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft_card WHERE season_draft_id = ?", Integer.class, committedDraftId));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft", Integer.class)); // the reports stay, they are the record of a season start
    }

    @Test
    void testRemove() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        assertEquals(1, seasonDraftRepository.countCardsAddedAt(PREPARED_AT, 21));

        SeasonRemovalCountsVO counts = removeSeasonInTransaction(draftId);

        assertEquals(3, counts.getCardSeasonDataRows());
        assertEquals(1, counts.getDeletedCards());
        assertEquals(1, counts.getUndoneRemaps());
        assertEquals(1, counts.getUndoneRenames());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE id = 21", Integer.class));
        assertEquals(SEASON_20_END_DATE, jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class));
        assertTrue(jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class).isAfter(SEASON_20_UPDATED_AT));
        assertEquals(21L, jdbcTemplate.queryForObject("SELECT nextval('season_id_seq')", Long.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card WHERE oracle_id = ?", Integer.class, FRESH_FACE));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card WHERE oracle_id = ?", Integer.class, JOVEN_NEW));
        assertEquals("Joven", jdbcTemplate.queryForObject("SELECT name FROM public.card WHERE oracle_id = ?", String.class, JOVEN_OLD));
        assertEquals("joven", jdbcTemplate.queryForObject("SELECT normalized_name FROM public.card WHERE oracle_id = ?", String.class, JOVEN_OLD));
        assertEquals(SEASON_20_ADDED_AT, jdbcTemplate.queryForObject("SELECT added_at FROM public.card WHERE oracle_id = ?", LocalDateTime.class, BLACK_LOTUS));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data WHERE season_id = 21", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card_season_data WHERE card_oracle_id = ?", Integer.class, JOVEN_OLD));
        assertEquals(REMOVED_AT, jdbcTemplate.queryForObject("SELECT removed_at FROM public.season_draft WHERE id = ?", LocalDateTime.class, draftId));
        assertEquals("raoul_zander", jdbcTemplate.queryForObject("SELECT removed_by FROM public.season_draft WHERE id = ?", String.class, draftId));
        assertNull(seasonDraftRepository.findCommittedDraft(21));
        assertNull(seasonDraftRepository.findDraft()); // the row is still there as the record of that start, it just isn't the draft any more
    }

    @Test
    void testRemove_withOpenDraft() {
        seedPreviousSeason();
        int removedDraftId = replaceDraft();
        commitDraftInTransaction(removedDraftId);
        removeSeasonInTransaction(removedDraftId);
        int draftId = replaceDraft();

        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
        assertEquals(draftId, seasonDraftRepository.findUncommittedDraft().getId());

        seasonDraftRepository.discard();

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season_draft", Integer.class));
        assertNull(seasonDraftRepository.findDraft());
        assertNull(seasonDraftRepository.findUncommittedDraft());
    }

    @Test
    void testRemove_withRemovedSeason() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        removeSeasonInTransaction(draftId);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> removeSeasonInTransaction(draftId));

        assertTrue(exception.getMessage().contains("was removed already"));
    }

    @Test
    void testRemove_withNewerSeason() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        jdbcTemplate.update("INSERT INTO public.season (id, season_number, start_date, end_date, updated_at) VALUES (22, 22, '2026-08-17', '2026-10-25', now())");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> removeSeasonInTransaction(draftId));

        assertTrue(exception.getMessage().contains("not the current season"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE id = 21", Integer.class));
    }

    @Test
    void testRemove_withAppliedMigration() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        seedAppliedMigration("db/changelog/migrations/20260608_0915_00_add_season_21.sql");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> removeSeasonInTransaction(draftId));
        jdbcTemplate.update("DELETE FROM public.databasechangelog WHERE id = 'season-migration'");

        assertTrue(exception.getMessage().contains("has run on this database"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE id = 21", Integer.class));
    }

    private static void seedAppliedMigration(String fileName) {
        jdbcTemplate.update("INSERT INTO public.databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype)"
                + " VALUES ('season-migration', 'raoul_zander', ?, now(), 999, 'EXECUTED')", fileName);
    }

    private static void seedPreviousSeason() {
        jdbcTemplate.update("DELETE FROM public.season_draft");
        jdbcTemplate.update("DELETE FROM public.card_season_data");
        jdbcTemplate.update("DELETE FROM public.card");
        jdbcTemplate.update("DELETE FROM public.season");

        jdbcTemplate.update("INSERT INTO public.season (id, season_number, start_date, end_date, updated_at) VALUES (20, 20, '2026-04-05', ?, ?)", SEASON_20_END_DATE, SEASON_20_UPDATED_AT);
        jdbcTemplate.update("INSERT INTO public.card (oracle_id, name, normalized_name, added_at) VALUES (?, 'Ancestor''s Chosen', 'ancestors-chosen', ?)", ANCESTORS_CHOSEN, SEASON_20_ADDED_AT);
        jdbcTemplate.update("INSERT INTO public.card (oracle_id, name, normalized_name, added_at) VALUES (?, 'Joven', 'joven', ?)", JOVEN_OLD, SEASON_20_ADDED_AT);
        jdbcTemplate.update("INSERT INTO public.card (oracle_id, name, normalized_name, added_at) VALUES (?, 'Black Lotus', 'black-lotus', ?)", BLACK_LOTUS, SEASON_20_ADDED_AT);
        jdbcTemplate.update("INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, vintage_restricted) VALUES (20, ?, 6, 'legal'::legality, FALSE)", ANCESTORS_CHOSEN);
        jdbcTemplate.update("INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, banned_in, vintage_restricted) VALUES (20, ?, 32, 'banned'::legality, 'legacy'::mtg_format, FALSE)", JOVEN_OLD);
        jdbcTemplate.update("INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, vintage_restricted) VALUES (20, ?, 599200, 'not_legal'::legality, TRUE)", BLACK_LOTUS);
    }

    private static int replaceDraft() {
        SeasonDraftVO draft = new SeasonDraftVO(
                0,
                21,
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 8, 16),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 6, 8),
                20,
                SEASON_20_END_DATE,
                SEASON_20_UPDATED_AT,
                "2026-06-07",
                "mtggoldfish",
                PREPARED_AT,
                "raoul_zander",
                null,
                null,
                null,
                null,
                null,
                null,
                "Season 21 (XXI), 2026-06-08 - 2026-08-16");

        List<SeasonDraftCardVO> cards = new ArrayList<>(4);
        cards.add(new SeasonDraftCardVO(ANCESTORS_CHOSEN, null, "Ancestor's Chosen", "ancestors-chosen", 12, Legality.EXTENDED,
                null, null, new BigDecimal("0.120"), null, null, null, null, false, false, null));
        cards.add(new SeasonDraftCardVO(JOVEN_NEW, JOVEN_OLD, "Joven and Chandler", "joven-and-chandler", 32, Legality.BANNED,
                null, null, null, null, null, null, MtgFormat.LEGACY, false, true, null));
        cards.add(new SeasonDraftCardVO(BLACK_LOTUS, null, "Black Lotus", "black-lotus", 599200, Legality.NOT_LEGAL,
                null, null, null, null, null, null, null, true, false, "duplicate normalized name"));
        cards.add(new SeasonDraftCardVO(FRESH_FACE, null, "Fresh Face", "fresh-face", 4, Legality.LEGAL,
                null, null, null, null, null, null, null, false, true, null));

        seasonDraftRepository.replace(draft, cards);
        return seasonDraftRepository.findDraft().getId();
    }

    private static CommittedSeasonCountsVO commitDraftInTransaction(int draftId) { // in the application the @Transactional proxy opens it
        return transactionTemplate.execute(transactionStatus -> seasonDraftRepository.commit(draftId, PREPARED_AT, COMMITTED_AT, "janik_nissen", null, null));
    }

    private static SeasonRemovalCountsVO removeSeasonInTransaction(int draftId) {
        return transactionTemplate.execute(transactionStatus -> seasonDraftRepository.remove(draftId, ORACLE_ID_CHANGES, RENAMED_CARDS, REMOVED_AT, "raoul_zander"));
    }
}

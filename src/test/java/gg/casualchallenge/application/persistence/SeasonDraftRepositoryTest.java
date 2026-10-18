package gg.casualchallenge.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.model.type.Finish;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.type.PriceProvider;
import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private static final int PRICE_DETAILS_SEASONS = 5;

    private static final List<SeasonDraftReportVO.OracleIdChangeVO> ORACLE_ID_CHANGES =
            List.of(new SeasonDraftReportVO.OracleIdChangeVO("Joven and Chandler", JOVEN_OLD, JOVEN_NEW, "ATQ"));
    private static final List<SeasonDraftReportVO.RenamedCardVO> RENAMED_CARDS =
            List.of(new SeasonDraftReportVO.RenamedCardVO(
                    JOVEN_NEW,
                    "Joven",
                    "joven",
                    "Joven and Chandler",
                    "joven-and-chandler"
            ));

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
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        seasonDraftRepository = new SeasonDraftRepository(jdbcTemplate, objectMapper);
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
        assertEquals(1, countRows("public.season WHERE id = 21 AND season_number = 21"));
        assertEquals(
                LocalDate.of(2026, 6, 8),
                jdbcTemplate.queryForObject("SELECT start_date FROM public.season WHERE id = 21", LocalDate.class)
        );
        assertEquals(
                LocalDate.of(2026, 8, 16),
                jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 21", LocalDate.class)
        );
        assertEquals(
                LocalDate.of(2026, 6, 7),
                jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class)
        );
        LocalDateTime season20UpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM public.season WHERE id = 20",
                LocalDateTime.class
        );
        assertTrue(season20UpdatedAt.isAfter(SEASON_20_UPDATED_AT));
        assertEquals(22L, jdbcTemplate.queryForObject("SELECT nextval('season_id_seq')", Long.class));
        assertEquals(4, countRows("public.card"));
        assertEquals(PREPARED_AT, selectFromCard("added_at", FRESH_FACE, LocalDateTime.class));
        assertEquals("Joven and Chandler", selectFromCard("name", JOVEN_NEW, String.class));
        assertEquals("joven-and-chandler", selectFromCard("normalized_name", JOVEN_NEW, String.class));
        assertEquals("Ancestor's Chosen", selectFromCard("name", ANCESTORS_CHOSEN, String.class));
        // The remapped card is flagged as new as well --> the insert has to run into the conflict
        assertEquals(SEASON_20_ADDED_AT, selectFromCard("added_at", JOVEN_NEW, LocalDateTime.class));
        assertEquals(0, countRows("public.card WHERE oracle_id = ?", JOVEN_OLD));
        assertEquals(2, countRows("public.card_season_data WHERE card_oracle_id = ?", JOVEN_NEW));
        assertEquals(0, countRows("public.card_season_data WHERE card_oracle_id = ?", JOVEN_OLD));
        assertEquals(
                6,
                jdbcTemplate.queryForObject(
                        "SELECT budget_points FROM public.card_season_data WHERE season_id = 20 AND card_oracle_id = ?",
                        Integer.class,
                        ANCESTORS_CHOSEN
                )
        );
        assertEquals(
                12,
                jdbcTemplate.queryForObject(
                        "SELECT budget_points FROM public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?",
                        Integer.class,
                        ANCESTORS_CHOSEN
                )
        );
        assertEquals(
                "extended",
                jdbcTemplate.queryForObject(
                        "SELECT legality::text FROM public.card_season_data WHERE season_id = 21"
                                + " AND card_oracle_id = ?",
                        String.class,
                        ANCESTORS_CHOSEN
                )
        );
        assertEquals(
                "legacy",
                jdbcTemplate.queryForObject(
                        "SELECT banned_in::text FROM public.card_season_data WHERE season_id = 21"
                                + " AND card_oracle_id = ?",
                        String.class,
                        JOVEN_NEW
                )
        );
        assertEquals(0, countRows("public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?", BLACK_LOTUS));
        assertEquals(3, countRows("public.card_season_data WHERE season_id = 21"));
        assertNotNull(seasonDraftRepository.findDraft().getCommittedAt());
        assertEquals(
                "janik_nissen",
                jdbcTemplate.queryForObject(
                        "SELECT committed_by FROM public.season_draft WHERE id = ?",
                        String.class,
                        draftId
                )
        );
        assertEquals("janik_nissen", seasonDraftRepository.findDraft().getCommittedBy());
        assertEquals("raoul_zander", seasonDraftRepository.findDraft().getPreparedBy());
        assertNull(seasonDraftRepository.findUncommittedDraft());
    }

    @Test
    void testCommit_withPriceDetails() {
        seedPreviousSeason();
        seedPriceDetailsOfPreviousSeason();
        int draftId = replaceDraft();

        commitDraftInTransaction(draftId);
        long ancestorsChosenId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.card_season_data WHERE season_id = 21 AND card_oracle_id = ?",
                Long.class,
                ANCESTORS_CHOSEN
        );
        CardPriceDetailsVO ancestorsChosen = seasonDraftRepository.findPriceDetails(ancestorsChosenId);

        assertEquals(2, countPriceDetails(21));
        assertEquals(priceDetails("10E", "Tenth Edition", "1", 12), ancestorsChosen);
        assertEquals("32", selectFromPriceDetails("details #>> '{cardmarket,averageCents}'", 21, JOVEN_NEW));
        assertEquals(3, countPriceDetails(20));
        assertEquals("{}", selectFromPriceDetails("details", 20, JOVEN_NEW));
    }

    @Test
    void testCommit_withOnePriceDetailsSeason() {
        seedPreviousSeason();
        seedPriceDetailsOfPreviousSeason();
        int draftId = replaceDraft();

        transactionTemplate.execute(transactionStatus ->
                seasonDraftRepository.commit(draftId, PREPARED_AT, COMMITTED_AT, "janik_nissen", null, null, 1));

        assertEquals(0, countPriceDetails(20));
        assertEquals(2, countPriceDetails(21));
        assertEquals(3, countRows("public.card_season_data WHERE season_id = 20"));
        assertEquals(0, countRows("public.season_draft_card WHERE price_details IS NOT NULL"));
    }

    @Test
    void testReplacePriceDetails() {
        seedPreviousSeason();
        seedPriceDetailsOfPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);

        int storedCards = seasonDraftRepository.replacePriceDetails(20, Map.of(
                ANCESTORS_CHOSEN, priceDetails("10E", "Tenth Edition", "1", 6),
                JOVEN_NEW, priceDetails("HML", "Homelands", "77", 32)
        ));

        assertEquals(2, storedCards);
        assertEquals(2, countPriceDetails(20));
        assertEquals("6", selectFromPriceDetails("details #>> '{cardmarket,averageCents}'", 20, ANCESTORS_CHOSEN));
        assertEquals("32", selectFromPriceDetails("details #>> '{cardmarket,averageCents}'", 20, JOVEN_NEW));
        assertEquals(2, countPriceDetails(21));
    }

    @Test
    void testCommit_withCommittedDraft() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> commitDraftInTransaction(draftId)
        );
        assertEquals("Draft for season 21 was already committed.", exception.getMessage());
    }

    @Test
    void testCommit_withStaleDraft() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        jdbcTemplate.update("UPDATE public.season SET updated_at = now() WHERE id = 20");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> commitDraftInTransaction(draftId)
        );
        assertEquals(
                "Draft for season 21 is stale, season 20 changed since the draft was prepared.",
                exception.getMessage()
        );
        assertEquals(0, countRows("public.season WHERE season_number = 21"));
        assertEquals(3, countRows("public.card"));
        assertEquals(3, countRows("public.card_season_data"));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testCommit_withFailingRemap() {
        seedPreviousSeason();
        jdbcTemplate.update(
                "INSERT INTO public.card (oracle_id, name, normalized_name, added_at)"
                        + " VALUES (?, 'Unrelated Card', 'unrelated-card', ?)",
                JOVEN_NEW,
                SEASON_20_ADDED_AT
        );
        int draftId = replaceDraft();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> commitDraftInTransaction(draftId)
        );
        assertEquals(
                "Card 'Joven and Chandler' is remapped to oracle id '"
                        + JOVEN_NEW
                        + "', which already belongs to 'Unrelated Card'.",
                exception.getMessage()
        );
        assertEquals(0, countRows("public.season WHERE season_number = 21"));
        assertEquals(
                SEASON_20_END_DATE,
                jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class)
        );
        assertEquals(
                SEASON_20_UPDATED_AT,
                jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class)
        );
        assertEquals(4, countRows("public.card"));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testCommit_withFailingRename() {
        seedPreviousSeason();
        jdbcTemplate.update(
                "INSERT INTO public.card (oracle_id, name, normalized_name, added_at)"
                        + " VALUES (?, 'Joven & Chandler', 'joven-and-chandler', ?)",
                OTHER_JOVEN,
                SEASON_20_ADDED_AT
        );
        int draftId = replaceDraft();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> commitDraftInTransaction(draftId)
        );
        assertEquals(
                "Card 'Joven' would be renamed to 'Joven and Chandler', which already belongs to 'Joven & Chandler'.",
                exception.getMessage()
        );
        assertEquals(0, countRows("public.season WHERE season_number = 21"));
        assertEquals(
                SEASON_20_UPDATED_AT,
                jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class)
        );
        assertEquals("Joven", selectFromCard("name", JOVEN_OLD, String.class));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testCommit_withUnknownCard() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        jdbcTemplate.update(
                "INSERT INTO public.season_draft_card (season_draft_id, oracle_id, name, normalized_name,"
                        + " budget_points, legality, vintage_restricted, is_new_card)"
                        + " VALUES (?, ?, 'Gone Card', 'gone-card', 4, 'legal'::legality, FALSE, FALSE)",
                draftId,
                GONE_CARD
        );

        assertThrows(DataIntegrityViolationException.class, () -> commitDraftInTransaction(draftId));
        assertEquals(0, countRows("public.season WHERE season_number = 21"));
        assertEquals(
                SEASON_20_END_DATE,
                jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class)
        );
        assertEquals(
                SEASON_20_UPDATED_AT,
                jdbcTemplate.queryForObject("SELECT updated_at FROM public.season WHERE id = 20", LocalDateTime.class)
        );
        assertEquals(3, countRows("public.card"));
        assertEquals(3, countRows("public.card_season_data"));
        assertEquals("Joven", selectFromCard("name", JOVEN_OLD, String.class));
        assertNull(seasonDraftRepository.findDraft().getCommittedAt());
    }

    @Test
    void testReplace() {
        seedPreviousSeason();
        replaceDraft();
        int draftId = replaceDraft();

        assertEquals(1, countRows("public.season_draft"));
        assertEquals(4, countRows("public.season_draft_card"));
        assertEquals(draftId, seasonDraftRepository.findDraft().getId());
        assertEquals(21, seasonDraftRepository.findDraft().getSeasonNumber());
        assertEquals(SEASON_20_UPDATED_AT, seasonDraftRepository.findDraft().getPreviousSeasonUpdatedAt());
        assertEquals(LocalDate.of(2026, 6, 7), seasonDraftRepository.findDraft().getMtgJsonDate());
        assertEquals(MetaShareSource.MTGGOLDFISH, seasonDraftRepository.findDraft().getMetaSource());
        assertEquals(
                "mtggoldfish",
                jdbcTemplate.queryForObject("SELECT meta_source FROM public.season_draft", String.class)
        );

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
        assertNull(cards.get(0).getPriceDetails());
        assertEquals("2026-03-30", selectFromDraftCard("price_details ->> 'priceWindowStart'", ANCESTORS_CHOSEN));
        assertEquals("cardmarket", selectFromDraftCard("price_details ->> 'pricedBy'", ANCESTORS_CHOSEN));
        assertEquals(
                "10E",
                selectFromDraftCard("price_details #>> '{cardmarket,printings,0,setCode}'", ANCESTORS_CHOSEN)
        );
        assertEquals(
                "nonfoil",
                selectFromDraftCard("price_details #>> '{cardmarket,printings,0,finish}'", ANCESTORS_CHOSEN)
        );
        assertNull(selectFromDraftCard("price_details", FRESH_FACE));
    }

    @Test
    void testMtgJsonDateMigration() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        jdbcTemplate.execute("ALTER TABLE public.season_draft ALTER COLUMN mtgjson_date TYPE character varying(31)");
        jdbcTemplate.update("UPDATE public.season_draft SET mtgjson_date = '2026-06-07' WHERE id = ?", draftId);
        ResourceDatabasePopulator migration = new ResourceDatabasePopulator(
                new ClassPathResource("db/changelog/migrations/20261018_2200_alter_season_draft_mtgjson_date.sql")
        );

        migration.execute(jdbcTemplate.getDataSource());

        assertEquals(
                "date",
                jdbcTemplate.queryForObject("SELECT pg_typeof(mtgjson_date) FROM public.season_draft", String.class)
        );
        assertEquals(LocalDate.of(2026, 6, 7), seasonDraftRepository.findDraft().getMtgJsonDate());
    }

    @Test
    void testFindDraftCard() {
        seedPreviousSeason();
        int committedDraftId = replaceDraft();
        commitDraftInTransaction(committedDraftId);
        int draftId = replaceDraft();

        SeasonDraftCardVO ancestorsChosen = seasonDraftRepository.findDraftCard(draftId, "ancestors-chosen");

        assertNull(seasonDraftRepository.findDraftCard(committedDraftId, "ancestors-chosen").getPriceDetails());
        assertEquals("Ancestor's Chosen", ancestorsChosen.getName());
        assertEquals(Legality.EXTENDED, ancestorsChosen.getLegality());
        assertEquals(priceDetails("10E", "Tenth Edition", "1", 12), ancestorsChosen.getPriceDetails());
        assertNull(seasonDraftRepository.findDraftCard(draftId, "fresh-face").getPriceDetails());
        assertNull(seasonDraftRepository.findDraftCard(draftId, "black-lotus"));
        assertNull(seasonDraftRepository.findDraftCard(draftId, "wonky-man"));
    }

    @Test
    void testReplace_withCommittedDraft() {
        seedPreviousSeason();
        int committedDraftId = replaceDraft();
        commitDraftInTransaction(committedDraftId);
        int draftId = replaceDraft();

        assertEquals(2, countRows("public.season_draft"));
        assertEquals(draftId, seasonDraftRepository.findDraft().getId());
        assertEquals(draftId, seasonDraftRepository.findUncommittedDraft().getId());
        // Newest committed --> its files can still be downloaded
        assertEquals(4, countRows("public.season_draft_card WHERE season_draft_id = ?", committedDraftId));

        seasonDraftRepository.discard();

        assertEquals(1, countRows("public.season_draft"));
        assertEquals(committedDraftId, seasonDraftRepository.findDraft().getId());
        assertNull(seasonDraftRepository.findUncommittedDraft());
    }

    @Test
    void testReplace_withOlderCommittedDrafts() {
        seedPreviousSeason();
        int olderDraftId = replaceDraft();
        commitDraftInTransaction(olderDraftId);
        // A second committed draft on top of the first
        jdbcTemplate.update("UPDATE public.season_draft SET season_number = 22 WHERE id = ?", olderDraftId);
        jdbcTemplate.update(
                "INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)"
                        + " VALUES (22, 22, '2026-08-17', '2026-10-25', ?)",
                SEASON_20_UPDATED_AT
        );
        int committedDraftId = replaceDraft();
        jdbcTemplate.update(
                "UPDATE public.season_draft SET committed_at = ? WHERE id = ?",
                COMMITTED_AT,
                committedDraftId
        );
        replaceDraft();

        assertEquals(0, countRows("public.season_draft_card WHERE season_draft_id = ?", olderDraftId));
        assertEquals(4, countRows("public.season_draft_card WHERE season_draft_id = ?", committedDraftId));
        assertEquals(3, countRows("public.season_draft")); // The reports stay, they are the record of a season start
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
        assertEquals(0, countRows("public.season WHERE id = 21"));
        assertEquals(
                SEASON_20_END_DATE,
                jdbcTemplate.queryForObject("SELECT end_date FROM public.season WHERE id = 20", LocalDate.class)
        );
        LocalDateTime season20UpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM public.season WHERE id = 20",
                LocalDateTime.class
        );
        assertTrue(season20UpdatedAt.isAfter(SEASON_20_UPDATED_AT));
        assertEquals(21L, jdbcTemplate.queryForObject("SELECT nextval('season_id_seq')", Long.class));
        assertEquals(3, countRows("public.card"));
        assertEquals(0, countRows("public.card WHERE oracle_id = ?", FRESH_FACE));
        assertEquals(0, countRows("public.card WHERE oracle_id = ?", JOVEN_NEW));
        assertEquals("Joven", selectFromCard("name", JOVEN_OLD, String.class));
        assertEquals("joven", selectFromCard("normalized_name", JOVEN_OLD, String.class));
        assertEquals(SEASON_20_ADDED_AT, selectFromCard("added_at", BLACK_LOTUS, LocalDateTime.class));
        assertEquals(0, countRows("public.card_season_data WHERE season_id = 21"));
        assertEquals(3, countRows("public.card_season_data"));
        assertEquals(1, countRows("public.card_season_data WHERE card_oracle_id = ?", JOVEN_OLD));
        assertEquals(
                REMOVED_AT,
                jdbcTemplate.queryForObject(
                        "SELECT removed_at FROM public.season_draft WHERE id = ?",
                        LocalDateTime.class,
                        draftId
                )
        );
        assertEquals(
                "raoul_zander",
                jdbcTemplate.queryForObject(
                        "SELECT removed_by FROM public.season_draft WHERE id = ?",
                        String.class,
                        draftId
                )
        );
        assertNull(seasonDraftRepository.findCommittedDraft(21));
        // The row is still there as the record of that start, it just isn't the draft any more
        assertNull(seasonDraftRepository.findDraft());
    }

    @Test
    void testRemove_withPriceDetails() {
        seedPreviousSeason();
        seedPriceDetailsOfPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);

        removeSeasonInTransaction(draftId);

        assertEquals(3, countRows("public.card_season_price_details"));
        assertEquals("{}", selectFromPriceDetails("details", 20, JOVEN_OLD));
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

        assertEquals(1, countRows("public.season_draft"));
        assertNull(seasonDraftRepository.findDraft());
        assertNull(seasonDraftRepository.findUncommittedDraft());
    }

    @Test
    void testRemove_withRemovedSeason() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        removeSeasonInTransaction(draftId);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> removeSeasonInTransaction(draftId)
        );

        assertTrue(exception.getMessage().contains("was removed already"));
    }

    @Test
    void testRemove_withNewerSeason() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        jdbcTemplate.update(
                "INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)"
                        + " VALUES (22, 22, '2026-08-17', '2026-10-25', now())");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> removeSeasonInTransaction(draftId)
        );

        assertTrue(exception.getMessage().contains("not the current season"));
        assertEquals(1, countRows("public.season WHERE id = 21"));
    }

    @Test
    void testRemove_withAppliedMigration() {
        seedPreviousSeason();
        int draftId = replaceDraft();
        commitDraftInTransaction(draftId);
        seedAppliedMigration("db/changelog/migrations/20260608_0915_00_add_season_21.sql");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> removeSeasonInTransaction(draftId)
        );
        jdbcTemplate.update("DELETE FROM public.databasechangelog WHERE id = 'season-migration'");

        assertTrue(exception.getMessage().contains("has run on this database"));
        assertEquals(1, countRows("public.season WHERE id = 21"));
    }

    private static void seedAppliedMigration(String fileName) {
        jdbcTemplate.update(
                "INSERT INTO public.databasechangelog"
                        + " (id, author, filename, dateexecuted, orderexecuted, exectype)"
                        + " VALUES ('season-migration', 'raoul_zander', ?, now(), 999, 'EXECUTED')",
                fileName
        );
    }

    private static void seedPreviousSeason() {
        jdbcTemplate.update("DELETE FROM public.season_draft");
        jdbcTemplate.update("DELETE FROM public.card_season_data");
        jdbcTemplate.update("DELETE FROM public.card");
        jdbcTemplate.update("DELETE FROM public.season");
        // The real season 21 migrations ran as well, and those block the removal
        jdbcTemplate.update("DELETE FROM public.databasechangelog");

        jdbcTemplate.update(
                "INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)"
                        + " VALUES (20, 20, '2026-04-05', ?, ?)",
                SEASON_20_END_DATE,
                SEASON_20_UPDATED_AT
        );
        jdbcTemplate.update(
                "INSERT INTO public.card (oracle_id, name, normalized_name, added_at)"
                        + " VALUES (?, 'Ancestor''s Chosen', 'ancestors-chosen', ?)",
                ANCESTORS_CHOSEN,
                SEASON_20_ADDED_AT
        );
        jdbcTemplate.update(
                "INSERT INTO public.card (oracle_id, name, normalized_name, added_at) VALUES (?, 'Joven', 'joven', ?)",
                JOVEN_OLD,
                SEASON_20_ADDED_AT
        );
        jdbcTemplate.update(
                "INSERT INTO public.card (oracle_id, name, normalized_name, added_at)"
                        + " VALUES (?, 'Black Lotus', 'black-lotus', ?)",
                BLACK_LOTUS,
                SEASON_20_ADDED_AT
        );
        jdbcTemplate.update(
                "INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality,"
                        + " vintage_restricted) VALUES (20, ?, 6, 'legal'::legality, FALSE)",
                ANCESTORS_CHOSEN
        );
        jdbcTemplate.update(
                "INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, banned_in,"
                        + " vintage_restricted) VALUES (20, ?, 32, 'banned'::legality, 'legacy'::mtg_format, FALSE)",
                JOVEN_OLD
        );
        jdbcTemplate.update(
                "INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality,"
                        + " vintage_restricted) VALUES (20, ?, 599200, 'not_legal'::legality, TRUE)",
                BLACK_LOTUS
        );
    }

    private static void seedPriceDetailsOfPreviousSeason() {
        jdbcTemplate.update("INSERT INTO public.card_season_price_details (card_season_data_id, details)"
                + " SELECT id, '{}' FROM public.card_season_data WHERE season_id = 20");
    }

    private static int countPriceDetails(int seasonId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.card_season_price_details price_details"
                        + " JOIN public.card_season_data data ON data.id = price_details.card_season_data_id"
                        + " WHERE data.season_id = ?",
                Integer.class,
                seasonId
        );
    }

    private static String selectFromPriceDetails(String expression, int seasonId, UUID oracleId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + expression + " FROM public.card_season_price_details price_details"
                        + " JOIN public.card_season_data data ON data.id = price_details.card_season_data_id"
                        + " WHERE data.season_id = ? AND data.card_oracle_id = ?",
                String.class,
                seasonId,
                oracleId
        );
    }

    private static Integer countRows(String tableAndCondition, Object... arguments) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class, arguments);
    }

    private static <T> T selectFromCard(String column, UUID oracleId, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM public.card WHERE oracle_id = ?",
                type,
                oracleId
        );
    }

    private static String selectFromDraftCard(String expression, UUID oracleId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + expression + " FROM public.season_draft_card WHERE oracle_id = ?",
                String.class,
                oracleId
        );
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
                LocalDate.of(2026, 6, 7),
                MetaShareSource.MTGGOLDFISH,
                PREPARED_AT,
                "raoul_zander",
                null,
                null,
                null,
                null,
                null,
                null,
                "Season 21 (XXI), 2026-06-08 - 2026-08-16"
        );

        List<SeasonDraftCardVO> cards = new ArrayList<>(4);
        cards.add(new SeasonDraftCardVO(ANCESTORS_CHOSEN, null, "Ancestor's Chosen", "ancestors-chosen", 12,
                Legality.EXTENDED, null, null, new BigDecimal("0.120"), null, null, null, null, false, false, null,
                priceDetails("10E", "Tenth Edition", "1", 12)));
        cards.add(new SeasonDraftCardVO(JOVEN_NEW, JOVEN_OLD, "Joven and Chandler", "joven-and-chandler", 32,
                Legality.BANNED, null, null, null, null, null, null, MtgFormat.LEGACY, false, true, null,
                priceDetails("HML", "Homelands", "77", 32)));
        cards.add(new SeasonDraftCardVO(BLACK_LOTUS, null, "Black Lotus", "black-lotus", 599200, Legality.NOT_LEGAL,
                null, null, null, null, null, null, null, true, false, "duplicate normalized name",
                priceDetails("LEA", "Limited Edition Alpha", "232", 599200)));
        cards.add(new SeasonDraftCardVO(FRESH_FACE, null, "Fresh Face", "fresh-face", 4, Legality.LEGAL,
                null, null, null, null, null, null, null, false, true, null, null));

        seasonDraftRepository.replace(draft, cards);
        return seasonDraftRepository.findDraft().getId();
    }

    private static CardPriceDetailsVO priceDetails(String setCode, String setName, String number, int cents) {
        CardPriceDetailsVO.PrintingPriceVO printing = new CardPriceDetailsVO.PrintingPriceVO(
                setCode,
                setName,
                number,
                Finish.NONFOIL,
                70,
                70,
                cents,
                cents,
                cents,
                true
        );
        CardPriceDetailsVO.MarketPricesVO cardmarket = new CardPriceDetailsVO.MarketPricesVO(
                70,
                cents,
                true,
                List.of(printing),
                Collections.nCopies(70, cents),
                Collections.nCopies(70, 0)
        );
        return new CardPriceDetailsVO(
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 6, 8),
                PriceProvider.CARDMARKET,
                null,
                cardmarket,
                new CardPriceDetailsVO.MarketPricesVO(0, 0, false, List.of(), List.of(), List.of()),
                List.of()
        );
    }

    /** In the application the {@code @Transactional} proxy opens it. */
    private static CommittedSeasonCountsVO commitDraftInTransaction(int draftId) {
        return transactionTemplate.execute(transactionStatus -> seasonDraftRepository.commit(
                draftId,
                PREPARED_AT,
                COMMITTED_AT,
                "janik_nissen",
                null,
                null,
                PRICE_DETAILS_SEASONS
        ));
    }

    private static SeasonRemovalCountsVO removeSeasonInTransaction(int draftId) {
        return transactionTemplate.execute(transactionStatus -> seasonDraftRepository.remove(
                draftId,
                ORACLE_ID_CHANGES,
                RENAMED_CARDS,
                REMOVED_AT,
                "raoul_zander"
        ));
    }
}

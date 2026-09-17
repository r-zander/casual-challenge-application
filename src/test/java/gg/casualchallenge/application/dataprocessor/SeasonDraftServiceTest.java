package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFile;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.type.MtgSetType;
import gg.casualchallenge.application.model.values.MtgSetVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeasonDraftServiceTest {

    private static final UUID ANCESTORS_CHOSEN = UUID.fromString("fc2ccab7-cab1-4463-b73d-898070136d74");
    private static final UUID JOVEN_NEW = UUID.fromString("11db8545-eca6-43f5-b9e8-f302acef53a5");
    private static final UUID JOVEN_OLD = UUID.fromString("86b47725-1764-4716-993d-e4dfcea2346c");

    private static final LocalDateTime PREPARED_AT = LocalDateTime.of(2026, 9, 13, 20, 21, 5);
    private static final LocalDateTime COMMITTED_AT = LocalDateTime.of(2026, 9, 15, 9, 5, 42);

    @Test
    void testSqlFileName() {
        SeasonDraftVO draft = draft("raoul_zander", null, null);

        assertEquals("20260913_2021_00_add_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.ADD_SEASON));
        assertEquals("20260913_2021_01_insert_cards.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARDS));
        assertEquals("20260913_2021_02_insert_card_season_data_for_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARD_SEASON_DATA));
    }

    @Test
    void testSqlFileName_withCommittedDraft() {
        SeasonDraftVO draft = draft("raoul_zander", COMMITTED_AT, "janik_nissen");

        assertEquals("20260915_0905_00_add_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.ADD_SEASON));
        assertEquals("20260915_0905_01_insert_cards.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARDS));
        assertEquals("20260915_0905_02_insert_card_season_data_for_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARD_SEASON_DATA));
    }

    @Test
    void testMigrationAuthor() {
        assertEquals("raoul_zander", SeasonDraftService.migrationAuthor(draft("raoul_zander", null, null)));
        assertEquals("janik_nissen", SeasonDraftService.migrationAuthor(draft("raoul_zander", COMMITTED_AT, "janik_nissen")));
        assertEquals("janik_nissen", SeasonDraftService.migrationAuthor(draft(null, COMMITTED_AT, "janik_nissen")));
    }

    @Test
    void testMigrationAuthor_withDraftOfAnOlderBuild() {
        SeasonDraftVO draft = draft(null, null, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> SeasonDraftService.migrationAuthor(draft));
        assertEquals("The draft for season 21 was prepared by an older build, prepare it again.", exception.getMessage());
    }

    @Test
    void testReport_roundTrip() throws JsonProcessingException {
        SeasonDraftReportVO report = report(); // the same route through Jackson the report takes into the season_draft column and back out
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SeasonDraftReportVO readBack = objectMapper.readValue(objectMapper.writeValueAsString(report), SeasonDraftReportVO.class);

        assertEquals(report, readBack);
    }

    @Test
    void testReport_withReportOfAnOlderSeason() throws JsonProcessingException {
        String storedReport = """
                {"seasonNumber": 20, "romanSeasonNumber": "XX", "metaSource": "mtggoldfish",
                 "counts": {"cards": 30206, "pricesFixedByExchangeRate": 63},
                 "setsReleased": [{"name": "Secrets of Strixhaven", "code": "SOS", "releaseDate": "2026-04-24", "type": "expansion", "commanderDecks": []}]}""";
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SeasonDraftReportVO report = objectMapper.readValue(storedReport, SeasonDraftReportVO.class);

        assertEquals(20, report.getSeasonNumber());
        assertEquals(30206, report.getCounts().getCards());
        assertEquals(0, report.getCounts().getPricesFixedByExchangeRateCount()); // the old name is gone, nothing to read it into
        assertNull(report.getDuplicateMetaShareNames());
        assertEquals(MtgSetType.EXPANSION, report.getSetsReleased().get(0).getType());
        assertNull(report.getSetsReleased().get(0).getChildCodes());
        assertEquals(0, report.getSetsReleased().get(0).getNewCardCount());
        assertNull(report.getPreparedBy());
        assertNull(report.getCommittedAt());
        assertNull(report.getCommittedBy());
    }

    private static SeasonDraftVO draft(String preparedBy, LocalDateTime committedAt, String committedBy) {
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
                preparedBy,
                committedAt,
                committedBy,
                "{}");
    }

    private static SeasonDraftReportVO report() {
        SeasonDraftReportVO.CountsVO counts = new SeasonDraftReportVO.CountsVO(
                30206,
                412,
                8901,
                Map.of(Legality.LEGAL, 20000, Legality.BANNED, 2),
                1.0834,
                1.0417,
                63,
                70,
                Map.of(MtgFormat.LEGACY, 50),
                Map.of(MtgFormat.LEGACY, 150),
                64, 66, 161, 155, 517, 894, 3, 3, 9, 1868);

        return new SeasonDraftReportVO(
                21,
                "XXI",
                LocalDate.of(2026, 9, 13),
                LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 22),
                LocalDate.of(2026, 7, 5),
                LocalDate.of(2026, 9, 13),
                LocalDate.of(2026, 9, 13),
                "5.2.2+20260913",
                "mtggoldfish",
                20,
                PREPARED_AT,
                "raoul_zander",
                null,
                null,
                counts,
                List.of("Lórien Revealed"),
                List.of(new SeasonDraftReportVO.BanChangeVO("Brainstorm", 300, Map.of(MtgFormat.LEGACY, new BigDecimal("0.400")), MtgFormat.LEGACY, false)),
                List.of(new SeasonDraftReportVO.BanChangeVO("Sol Ring", 50, Map.of(), null, false)),
                List.of(),
                List.of(),
                List.of(new SeasonDraftReportVO.BudgetPointChangeVO("Black Lotus", 500000, 599200, 99200)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SeasonDraftReportVO.LeftOutCardVO("Bee-Bee Gun", null, "no eligible printing")),
                List.of(new SeasonDraftReportVO.LeftOutCardVO("Lava, Axe", null, "duplicate normalized name")),
                List.of(new SeasonDraftReportVO.OracleIdChangeVO("Joven and Chandler", JOVEN_OLD, JOVEN_NEW, "ATQ")),
                List.of(new SeasonDraftReportVO.RenamedCardVO(JOVEN_NEW, "Joven", "joven", "Joven and Chandler", "joven-and-chandler")),
                List.of(new SeasonDraftReportVO.RenamedCardVO(ANCESTORS_CHOSEN, "Ancestor's Chosen", "ancestor-s-chosen", "Ancestor's Chosen", "ancestors-chosen")),
                List.of(new MtgSetVO("Secrets of Strixhaven", "SOS", LocalDate.of(2026, 4, 24), MtgSetType.EXPANSION, List.of("Lorehold Spirit"), List.of("SOC"), 3)),
                new SeasonDraftReportVO.ScryfallDecksVO("Black Lotus\nBrainstorm", "Sol Ring", "Brainstorm"));
    }
}

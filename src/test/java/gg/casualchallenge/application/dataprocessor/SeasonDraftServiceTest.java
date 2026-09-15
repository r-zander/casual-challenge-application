package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFile;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
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

class SeasonDraftServiceTest {

    private static final UUID JOVEN_NEW = UUID.fromString("2d8e4f10-3b6c-4d5e-9a7f-8b0c1d2e3f40");
    private static final UUID JOVEN_OLD = UUID.fromString("7a4b1c0e-9f2d-4a3b-8c7d-1e5f6a2b3c4d");

    private static final LocalDateTime PREPARED_AT = LocalDateTime.of(2026, 9, 13, 20, 21, 5);
    private static final LocalDateTime COMMITTED_AT = LocalDateTime.of(2026, 9, 15, 9, 5, 42);

    @Test
    void testSqlFileName() {
        SeasonDraftVO draft = draft(null);

        assertEquals("20260913_2021_00_add_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.ADD_SEASON));
        assertEquals("20260913_2021_01_insert_cards.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARDS));
        assertEquals("20260913_2021_02_insert_card_season_data_for_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARD_SEASON_DATA));
    }

    @Test
    void testSqlFileName_withCommittedDraft() {
        SeasonDraftVO draft = draft(COMMITTED_AT);

        assertEquals("20260915_0905_00_add_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.ADD_SEASON));
        assertEquals("20260915_0905_01_insert_cards.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARDS));
        assertEquals("20260915_0905_02_insert_card_season_data_for_season_21.sql", SeasonDraftService.sqlFileName(draft, SeasonSqlFile.INSERT_CARD_SEASON_DATA));
    }

    /** The report takes the same route through Jackson on its way into the season_draft column and back out. */
    @Test
    void testReport_roundTrip() throws JsonProcessingException {
        SeasonDraftReportVO report = report();
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SeasonDraftReportVO readBack = objectMapper.readValue(objectMapper.writeValueAsString(report), SeasonDraftReportVO.class);

        assertEquals(report, readBack);
    }

    private static SeasonDraftVO draft(LocalDateTime committedAt) {
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
                committedAt,
                "{}");
    }

    private static SeasonDraftReportVO report() {
        SeasonDraftReportVO.CountsVO counts = new SeasonDraftReportVO.CountsVO(
                30206,
                412,
                1868,
                8901,
                Map.of(Legality.LEGAL, 20000, Legality.BANNED, 2),
                1.0834,
                1.0417,
                63,
                Map.of(MtgFormat.LEGACY, 50),
                Map.of(MtgFormat.LEGACY, 150));

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
                counts,
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
                List.of(new SeasonDraftReportVO.RenamedCardVO(JOVEN_NEW, "Joven", "Joven and Chandler", "joven-and-chandler")),
                List.of(new SeasonDraftReportVO.RenamedCardVO(UUID.fromString("fc2ccab7-cab1-4463-b73d-898070136d74"), "Ancestor's Chosen", "Ancestor's Chosen", "ancestors-chosen")),
                List.of(new MtgSetVO("Edge of Eternities", "EOE", LocalDate.of(2026, 10, 2), "expansion", List.of("Cosmic Conquest"))),
                new SeasonDraftReportVO.ScryfallDecksVO("Black Lotus\nBrainstorm", "Sol Ring", "Brainstorm"));
    }
}

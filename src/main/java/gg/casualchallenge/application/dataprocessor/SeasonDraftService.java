package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.api.CasualChallengeService;
import gg.casualchallenge.application.common.RomanNumeral;
import gg.casualchallenge.application.common.SeasonDates;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFile;
import gg.casualchallenge.application.model.values.CommittedSeasonCountsVO;
import gg.casualchallenge.application.model.values.CommittedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.model.values.SeasonSqlFileVO;
import gg.casualchallenge.application.persistence.SeasonDraftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class SeasonDraftService {

    private static final DateTimeFormatter FILE_NAME_PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final SeasonDraftRepository seasonDraftRepository;
    private final CasualChallengeService casualChallengeService;
    private final ObjectMapper objectMapper;
    private final String exportDirectory;
    private final String migrationAuthor;

    public SeasonDraftService(
            SeasonDraftRepository seasonDraftRepository,
            CasualChallengeService casualChallengeService,
            ObjectMapper objectMapper,
            @Value("${casual-challenge.season.export-directory}") String exportDirectory,
            @Value("${casual-challenge.season.migration-author}") String migrationAuthor
    ) {
        this.seasonDraftRepository = seasonDraftRepository;
        this.casualChallengeService = casualChallengeService;
        this.objectMapper = objectMapper;
        this.exportDirectory = exportDirectory;
        this.migrationAuthor = migrationAuthor;
    }

    public SeasonDraftReportVO report() {
        return toReport(latestDraft());
    }

    public CommittedSeasonVO commit() {
        SeasonDraftVO draft = uncommittedDraft("commit");

        // prepared_at is what the exported migration writes into card.added_at, so the database gets the very same value
        CommittedSeasonCountsVO counts = seasonDraftRepository.commit(draft.getId(), draft.getPreparedAt());
        log.info("Committed season {}. {} cards added, {} renamed, {} remapped, {} season data rows written.",
                draft.getSeasonNumber(), counts.getInsertedCards(), counts.getRenamedCards(), counts.getRemappedCards(), counts.getUpsertedCardSeasonData());

        SeasonDraftVO committedDraft = seasonDraftRepository.findDraft(); // committed_at decides the migration file names
        if (!exportDirectory.isEmpty()) {
            try {
                writeSqlFiles(committedDraft);
            } catch (IOException e) {
                throw new IllegalStateException("Season " + committedDraft.getSeasonNumber() + " is committed, but writing the migration files failed: " + e.getMessage(), e);
            }
        }

        try {
            casualChallengeService.preloadCards();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Season " + committedDraft.getSeasonNumber() + " is committed, but reloading the card cache failed: " + e.getMessage(), e);
        }

        SeasonDraftReportVO report = toReport(committedDraft);

        return new CommittedSeasonVO(
                committedDraft.getSeasonNumber(),
                RomanNumeral.of(committedDraft.getSeasonNumber()),
                committedDraft.getStartDate(),
                SeasonDates.finalsFriday(committedDraft.getEndDate()),
                committedDraft.getEndDate(),
                SeasonDates.nextSeasonStart(committedDraft.getEndDate()),
                report.getSetsReleased(),
                report.getScryfallDecks(),
                counts
        );
    }

    public void discard() {
        uncommittedDraft("discard");
        seasonDraftRepository.discard();
    }

    public SeasonSqlFileVO exportSql(SeasonSqlFile part) {
        SeasonDraftVO draft = latestDraft();

        return new SeasonSqlFileVO(sqlFileName(draft, part), sqlContent(draft, part));
    }

    /** All three files share the prefix, so 00_add_season is applied before the data it needs. */
    public static String sqlFileName(SeasonDraftVO draft, SeasonSqlFile part) {
        LocalDateTime writtenAt = draft.getCommittedAt() != null ? draft.getCommittedAt() : draft.getPreparedAt();
        String fileName = writtenAt.format(FILE_NAME_PREFIX_FORMAT) + "_" + part.getPart();
        if (!part.getSeasonSuffix().isEmpty()) {
            fileName = fileName + part.getSeasonSuffix() + draft.getSeasonNumber();
        }

        return fileName + ".sql";
    }

    private void writeSqlFiles(SeasonDraftVO draft) throws IOException {
        Path directory = Paths.get(exportDirectory);
        Files.createDirectories(directory);
        for (SeasonSqlFile part : SeasonSqlFile.values()) {
            Path file = directory.resolve(sqlFileName(draft, part));
            Files.writeString(file, sqlContent(draft, part), StandardCharsets.UTF_8);
            log.info("Wrote season migration {}.", file);
        }
    }

    private String sqlContent(SeasonDraftVO draft, SeasonSqlFile part) {
        switch (part) {
            case ADD_SEASON:
                return addSeason(draft);
            case INSERT_CARDS:
                return SeasonMigrationSql.insertCards(seasonDraftRepository.findDraftCards(draft.getId()), draft.getPreparedAt());
            case INSERT_CARD_SEASON_DATA:
                return SeasonMigrationSql.insertCardSeasonData(draft.getSeasonNumber(), seasonDraftRepository.findDraftCards(draft.getId()));
        }

        throw new IllegalArgumentException("Don't know how to write the migration for '" + part + "'.");
    }

    private String addSeason(SeasonDraftVO draft) {
        SeasonDraftReportVO report = toReport(draft);

        return SeasonMigrationSql.addSeason(
                migrationAuthor,
                sqlFileName(draft, SeasonSqlFile.ADD_SEASON),
                draft,
                report.getOracleIdChanges(),
                report.getRenamedCards());
    }

    private SeasonDraftVO latestDraft() {
        SeasonDraftVO draft = seasonDraftRepository.findDraft();
        if (draft == null) {
            throw new IllegalStateException("There is no season draft.");
        }

        return draft;
    }

    private SeasonDraftVO uncommittedDraft(String action) {
        SeasonDraftVO draft = seasonDraftRepository.findUncommittedDraft();
        if (draft == null) {
            throw new IllegalStateException("There is no uncommitted season draft to " + action + " (a newer prepare may have replaced it).");
        }

        return draft;
    }

    private SeasonDraftReportVO toReport(SeasonDraftVO draft) {
        try {
            return objectMapper.readValue(draft.getReport(), SeasonDraftReportVO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't read the report of the draft for season " + draft.getSeasonNumber() + ".", e);
        }
    }
}

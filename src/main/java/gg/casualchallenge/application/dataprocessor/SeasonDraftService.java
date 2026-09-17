package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.api.CasualChallengeService;
import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.common.RomanNumeral;
import gg.casualchallenge.application.common.SeasonDates;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFile;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFileVO;
import gg.casualchallenge.application.model.values.CommittedSeasonCountsVO;
import gg.casualchallenge.application.model.values.CommittedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
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
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SeasonDraftService {

    private static final DateTimeFormatter FILE_NAME_PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final SeasonDraftRepository seasonDraftRepository;
    private final CasualChallengeService casualChallengeService;
    private final SeasonDates seasonDates;
    private final ObjectMapper objectMapper;
    private final String exportDirectory;

    public SeasonDraftService(
            SeasonDraftRepository seasonDraftRepository,
            CasualChallengeService casualChallengeService,
            SeasonDates seasonDates,
            ObjectMapper objectMapper,
            @Value("${casual-challenge.season.export-directory}") String exportDirectory
    ) {
        this.seasonDraftRepository = seasonDraftRepository;
        this.casualChallengeService = casualChallengeService;
        this.seasonDates = seasonDates;
        this.objectMapper = objectMapper;
        this.exportDirectory = exportDirectory;
    }

    public SeasonDraftReportVO report() {
        SeasonDraftVO draft = seasonDraftRepository.findDraft();
        if (draft == null) return null;

        return toReport(draft);
    }

    public CommittedSeasonVO commit(String committedBy) {
        SeasonDraftVO draft = uncommittedDraft(DraftAction.COMMIT);

        CommittedSeasonCountsVO counts;
        casualChallengeService.lockCards();
        try {
            // prepared_at is what the exported migration writes into card.added_at, so the database gets the very same value
            counts = seasonDraftRepository.commit(draft.getId(), draft.getPreparedAt(), LocalDateTime.now(Constants.TIMEZONE), committedBy);
            try {
                casualChallengeService.preloadCards();
            } catch (RuntimeException e) {
                throw new RuntimeException("Season " + draft.getSeasonNumber() + " is committed, but reloading the card cache failed: " + e.getMessage() + " Call POST /admin/v1/cards/reload.", e);
            }
        } finally {
            casualChallengeService.unlockCards();
        }

        log.info("Committed season {}. {} cards added, {} names or normalized names updated, {} remapped, {} season data rows written.",
                draft.getSeasonNumber(), counts.getInsertedCards(), counts.getUpdatedCardNames(), counts.getRemappedCards(), counts.getUpsertedCardSeasonData());

        SeasonDraftVO committedDraft = seasonDraftRepository.findDraft(); // committed_at decides the migration file names
        if (!exportDirectory.isEmpty()) {
            try {
                writeSqlFiles(committedDraft);
            } catch (IOException e) {
                throw new RuntimeException("Season " + committedDraft.getSeasonNumber() + " is committed, but writing the migration files failed: " + e.getMessage(), e);
            }
        }

        SeasonDraftReportVO report = toReport(committedDraft);

        return new CommittedSeasonVO(
                committedDraft.getSeasonNumber(),
                RomanNumeral.of(committedDraft.getSeasonNumber()),
                committedDraft.getStartDate(),
                seasonDates.finalsFriday(committedDraft.getEndDate()),
                committedDraft.getEndDate(),
                seasonDates.nextSeasonStart(committedDraft.getEndDate()),
                report.getSetsReleased(),
                report.getScryfallDecks(),
                counts
        );
    }

    public void discard() {
        uncommittedDraft(DraftAction.DISCARD);
        seasonDraftRepository.discard();
    }

    public SeasonSqlFileVO exportSql(SeasonSqlFile part) {
        SeasonDraftVO draft = seasonDraftRepository.findDraft();
        if (draft == null) return null;

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

    // Same switch as sqlFileName
    public static String migrationAuthor(SeasonDraftVO draft) {
        String author = draft.getCommittedAt() != null ? draft.getCommittedBy() : draft.getPreparedBy();
        if (author == null) {
            throw new IllegalStateException("The draft for season " + draft.getSeasonNumber() + " was prepared by an older build, prepare it again.");
        }

        return author;
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

        // A replay on an untouched database needs the repaired normalized names as well, not just the real renames
        List<SeasonDraftReportVO.RenamedCardVO> renamedCards = new ArrayList<>(report.getRenamedCards().size() + report.getNormalizedNameFixes().size());
        renamedCards.addAll(report.getRenamedCards());
        renamedCards.addAll(report.getNormalizedNameFixes());
        for (SeasonDraftReportVO.RenamedCardVO renamedCard : renamedCards) {
            if (renamedCard.getPreviousNormalizedName() == null) { // a report from before that field existed, the UPDATE would be written with a NULL in it
                throw new IllegalStateException("The draft for season " + draft.getSeasonNumber() + " was prepared by an older build, prepare it again.");
            }
        }

        return SeasonMigrationSql.addSeason(
                migrationAuthor(draft),
                sqlFileName(draft, SeasonSqlFile.ADD_SEASON),
                draft,
                report.getOracleIdChanges(),
                renamedCards);
    }

    private SeasonDraftVO uncommittedDraft(DraftAction action) {
        SeasonDraftVO draft = seasonDraftRepository.findUncommittedDraft();
        if (draft != null) return draft;

        SeasonDraftVO latestDraft = seasonDraftRepository.findDraft();
        if (latestDraft != null) {
            throw new IllegalStateException("Draft for season " + latestDraft.getSeasonNumber() + " was already committed.");
        }

        throw new IllegalStateException("There is no season draft to " + action + ".");
    }

    private SeasonDraftReportVO toReport(SeasonDraftVO draft) {
        try {
            return objectMapper.readValue(draft.getReport(), SeasonDraftReportVO.class)
                    .withCommittedAt(draft.getCommittedAt())
                    .withCommittedBy(draft.getCommittedBy());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't read the report of the draft for season " + draft.getSeasonNumber() + ".", e);
        }
    }

    private enum DraftAction {
        COMMIT,
        DISCARD,
        ;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }
}

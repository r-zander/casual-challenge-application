package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.api.CasualChallengeService;
import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.model.values.RemovedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.model.values.SeasonRemovalCountsVO;
import gg.casualchallenge.application.model.values.SeasonRemovalPreviewVO;
import gg.casualchallenge.application.persistence.SeasonDraftRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Season;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * Undoing a season start, for the rehearsal before the real one. The hard stop is Liquibase: once a migration for the season
 * has run here, the season is part of the repository and the next deployment would write it straight back.
 */
@Service
@Slf4j
public class SeasonRemovalService {

    private final SeasonDraftRepository seasonDraftRepository;
    private final SeasonRepository seasonRepository;
    private final CasualChallengeService casualChallengeService;
    private final GitHubClient gitHubClient;
    private final ObjectMapper objectMapper;
    private final Path archiveDirectory;

    public SeasonRemovalService(
            SeasonDraftRepository seasonDraftRepository,
            SeasonRepository seasonRepository,
            CasualChallengeService casualChallengeService,
            GitHubClient gitHubClient,
            ObjectMapper objectMapper,
            @Value("${casual-challenge.season.archive-directory}") String archiveDirectory
    ) {
        this.seasonDraftRepository = seasonDraftRepository;
        this.seasonRepository = seasonRepository;
        this.casualChallengeService = casualChallengeService;
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
        this.archiveDirectory = Paths.get(archiveDirectory);
    }

    public boolean exists(int seasonNumber) {
        return seasonRepository.findBySeasonNumber(seasonNumber) != null;
    }

    public SeasonRemovalPreviewVO preview(int seasonNumber) {
        Season season = seasonRepository.findBySeasonNumber(seasonNumber);
        if (season == null) return null;

        SeasonDraftVO draft = seasonDraftRepository.findCommittedDraft(seasonNumber);
        List<String> refusals = refusals(seasonNumber, draft);
        SeasonDraftReportVO report = draft != null ? toReport(draft) : null;

        return new SeasonRemovalPreviewVO(
                seasonNumber,
                season.getStartDate(),
                season.getEndDate(),
                draft != null ? draft.getCommittedAt() : null,
                draft != null ? draft.getCommittedBy() : null,
                refusals.isEmpty(),
                refusals,
                seasonDraftRepository.countCardSeasonData(season.getId()),
                draft != null ? seasonDraftRepository.countCardsAddedAt(draft.getPreparedAt(), season.getId()) : 0,
                report != null ? report.getOracleIdChanges().size() : 0,
                report != null ? report.getRenamedCards().size() + report.getNormalizedNameFixes().size() : 0,
                report != null ? report.getPreviousSeasonNumber() : null,
                draft != null ? draft.getPreviousSeasonEndDate() : null,
                draft != null ? draft.getPullRequestUrl() : null,
                archiveDirectory.resolve("season-" + seasonNumber).toString());
    }

    /** @param githubToken only needed when the commit opened a pull request */
    public RemovedSeasonVO remove(int seasonNumber, String removedBy, String githubToken) {
        SeasonDraftVO draft = seasonDraftRepository.findCommittedDraft(seasonNumber);
        List<String> refusals = refusals(seasonNumber, draft);
        if (!refusals.isEmpty()) {
            throw new IllegalStateException(String.join(" ", refusals));
        }

        SeasonDraftReportVO report = toReport(draft);

        // GitHub first, same as the commit: while the branch is still up there, nothing in the database has moved
        String closedPullRequestUrl = null;
        if (draft.getPullRequestUrl() != null) {
            if (githubToken == null || githubToken.isEmpty()) {
                throw new IllegalStateException("The migrations of season " + seasonNumber + " went to " + draft.getPullRequestUrl()
                        + " --> pass a GitHub token and branch '" + draft.getMigrationBranch() + "' goes away with the season.");
            }
            gitHubClient.deleteBranch(githubToken, draft.getMigrationBranch());
            closedPullRequestUrl = draft.getPullRequestUrl();
        }

        // The repaired normalized names were written by the same commit, so they go back the same way
        List<SeasonDraftReportVO.RenamedCardVO> renamedCards = new ArrayList<>(report.getRenamedCards().size() + report.getNormalizedNameFixes().size());
        renamedCards.addAll(report.getRenamedCards());
        renamedCards.addAll(report.getNormalizedNameFixes());

        SeasonRemovalCountsVO counts;
        casualChallengeService.lockCards();
        try {
            counts = seasonDraftRepository.remove(
                    draft.getId(),
                    report.getOracleIdChanges(),
                    renamedCards,
                    LocalDateTime.now(Constants.TIMEZONE),
                    removedBy);
            try {
                casualChallengeService.preloadCards();
            } catch (RuntimeException e) {
                throw new RuntimeException("Season " + seasonNumber + " is removed, but reloading the card cache failed: " + e.getMessage() + " Call POST /admin/v1/cards/reload.", e);
            }
        } finally {
            casualChallengeService.unlockCards();
        }

        boolean archiveDeleted = SeasonPreparationService.deleteArchive(archiveDirectory, seasonNumber);

        log.info("Removed season {}. {} season data rows and {} cards are gone, {} renames and {} oracle ids went back, season {} ends {} again.",
                seasonNumber, counts.getCardSeasonDataRows(), counts.getDeletedCards(), counts.getUndoneRenames(), counts.getUndoneRemaps(),
                report.getPreviousSeasonNumber(), draft.getPreviousSeasonEndDate());

        return new RemovedSeasonVO(
                seasonNumber,
                report.getPreviousSeasonNumber(),
                draft.getPreviousSeasonEndDate(),
                closedPullRequestUrl,
                archiveDeleted,
                counts);
    }

    private List<String> refusals(int seasonNumber, SeasonDraftVO draft) {
        List<String> refusals = new ArrayList<>();

        if (seasonDraftRepository.hasAppliedSeasonMigration(seasonNumber)) {
            refusals.add("A migration for season " + seasonNumber + " has run on this database --> the season is part of the repository and the next deployment would write it again.");
        }

        Season currentSeason = seasonRepository.findCurrentSeason();
        if (currentSeason == null || currentSeason.getSeasonNumber() != seasonNumber) {
            refusals.add("Season " + seasonNumber + " is not the current season, and only the newest one can be removed.");
        }

        if (draft == null) {
            refusals.add("Season " + seasonNumber + " was not committed by the season wizard, so there is no record of what its commit did and nothing to undo it with.");
        } else if (draft.getPreviousSeasonEndDate() == null) {
            refusals.add("The draft for season " + seasonNumber + " was prepared by an older build, it never stored the end date season " + draft.getPreviousSeasonId() + " had before the commit.");
        }

        return refusals;
    }

    private SeasonDraftReportVO toReport(SeasonDraftVO draft) {
        try {
            return objectMapper.readValue(draft.getReport(), SeasonDraftReportVO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't read the report of the draft for season " + draft.getSeasonNumber() + ".", e);
        }
    }
}

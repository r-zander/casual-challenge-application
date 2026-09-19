package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFileVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubClientTest {

    private static final LocalDateTime PREPARED_AT = LocalDateTime.of(2026, 11, 22, 10, 42, 17);
    private static final LocalDateTime COMMITTED_AT = LocalDateTime.of(2026, 11, 22, 10, 44, 3);

    @Test
    void testBranchName() {
        assertEquals("feature/season-22-migrations", GitHubClient.branchName(22));
        assertEquals("feature/season-7-migrations", GitHubClient.branchName(7));
    }

    @Test
    void testCommitMessage() {
        assertEquals("Added migrations for newest season (22)", GitHubClient.commitMessage(22));
    }

    @Test
    void testMigrationPath() {
        assertEquals("src/main/resources/db/changelog/migrations/20261122_1044_00_add_season_22.sql",
                GitHubClient.migrationPath("20261122_1044_00_add_season_22.sql"));
    }

    @Test
    void testPullRequestBody() {
        assertEquals("""
                Season 22 migrations, straight out of the season wizard.

                - prepared 2026-11-22 10:42 by raoul_zander, committed by janik_nissen
                - 20261122_1044_00_add_season_22.sql
                - 20261122_1044_01_insert_cards.sql
                - 20261122_1044_02_insert_card_season_data_for_season_22.sql
                - the season is live already, these files are only so the database can be rebuilt from the repository alone
                - merge, then run the deploy workflow
                """, GitHubClient.pullRequestBody(draft(), files()));
    }

    private static SeasonDraftVO draft() {
        return new SeasonDraftVO(
                8,
                22,
                LocalDate.of(2026, 11, 22),
                LocalDate.of(2027, 1, 30),
                LocalDate.of(2026, 9, 13),
                LocalDate.of(2026, 11, 22),
                21,
                LocalDate.of(2026, 11, 21),
                LocalDateTime.of(2026, 9, 13, 20, 21),
                "2026-11-22",
                "mtggoldfish",
                PREPARED_AT,
                "raoul_zander",
                COMMITTED_AT,
                "janik_nissen",
                null,
                null,
                null,
                null,
                "{}");
    }

    private static List<SeasonSqlFileVO> files() {
        return List.of(
                new SeasonSqlFileVO("20261122_1044_00_add_season_22.sql", "-- liquibase formatted sql\n"),
                new SeasonSqlFileVO("20261122_1044_01_insert_cards.sql", "INSERT INTO public.card ...\n"),
                new SeasonSqlFileVO("20261122_1044_02_insert_card_season_data_for_season_22.sql", "INSERT INTO public.card_season_data ...\n"));
    }
}

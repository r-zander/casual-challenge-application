package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.persistence.converters.LegalityConverter;
import gg.casualchallenge.application.persistence.converters.MtgFormatConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SeasonMigrationSql { // the files data-preparer.py used to write, so a season start stays reproducible from the repository alone

    private static final int CHUNK_SIZE = 1000;

    // Seasons are prepared in UTC, so the offset never changes
    private static final DateTimeFormatter ADDED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+00:00'");

    private static final LegalityConverter LEGALITY_CONVERTER = new LegalityConverter();
    private static final MtgFormatConverter MTG_FORMAT_CONVERTER = new MtgFormatConverter();

    private SeasonMigrationSql() {}

    public static String addSeason(
            String author,
            String fileName,
            SeasonDraftVO draft,
            List<SeasonDraftReportVO.OracleIdChangeVO> oracleIdChanges,
            List<SeasonDraftReportVO.RenamedCardVO> renamedCards
    ) {
        // Commit already wrote all of this into the database, the file only has to survive a replay on a fresh one
        StringBuilder sql = new StringBuilder("""
                -- liquibase formatted sql

                -- changeset %s:%s
                UPDATE public.season
                    SET end_date = '%s',
                        updated_at = now()
                    WHERE id = %d;
                INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
                VALUES
                    (%d, %d, '%s', '%s', now())
                ON CONFLICT (id) DO NOTHING;
                SELECT setval('season_id_seq', (SELECT MAX(id) FROM public.season));
                """.formatted(
                author,
                fileName,
                draft.getStartDate().minusDays(1),
                draft.getPreviousSeasonId(),
                draft.getSeasonNumber(),
                draft.getSeasonNumber(),
                draft.getStartDate(),
                draft.getEndDate()));

        if (!oracleIdChanges.isEmpty()) {
            sql.append("\n");
            sql.append("-- card_season_data references card.oracle_id without ON UPDATE CASCADE --> both updates have to be one statement\n"); // keep in sync with SeasonDraftRepository.commit
            for (SeasonDraftReportVO.OracleIdChangeVO oracleIdChange : oracleIdChanges) {
                sql.append("WITH remapped_card AS (UPDATE public.card SET oracle_id = " + escapeUuid(oracleIdChange.getOracleId())
                        + " WHERE oracle_id = " + escapeUuid(oracleIdChange.getPreviousOracleId()) + ")"
                        + " UPDATE public.card_season_data SET card_oracle_id = " + escapeUuid(oracleIdChange.getOracleId())
                        + " WHERE card_oracle_id = " + escapeUuid(oracleIdChange.getPreviousOracleId())
                        + "; -- " + oracleIdChange.getName() + "\n");
            }
        }

        if (!renamedCards.isEmpty()) {
            sql.append("\n");
            for (SeasonDraftReportVO.RenamedCardVO renamedCard : renamedCards) {
                sql.append("UPDATE public.card SET name = " + escapeString(renamedCard.getName())
                        + ", normalized_name = " + escapeString(renamedCard.getNormalizedName())
                        + " WHERE oracle_id = " + escapeUuid(renamedCard.getOracleId())
                        + "; -- was " + escapeString(renamedCard.getPreviousName()) + " / " + escapeString(renamedCard.getPreviousNormalizedName()) + "\n");
            }
        }

        return sql.toString();
    }

    /*
        CREATE TABLE IF NOT EXISTS public.card
        (
            id serial NOT NULL,
            oracle_id uuid NOT NULL,
            name character varying(1023) NOT NULL,
            normalized_name character varying(1023) NOT NULL,
            added_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
            PRIMARY KEY (id)
        );
     */
    public static String insertCards(List<SeasonDraftCardVO> cards, LocalDateTime addedAt) { // keep in sync with SeasonDraftRepository.commit
        String addedAtValue = "'" + addedAt.format(ADDED_AT_FORMAT) + "'";

        StringBuilder sql = new StringBuilder();
        List<String> values = new ArrayList<>(CHUNK_SIZE);
        // Every card, not just the new ones: a database rebuilt from the migrations alone has to end up complete. The python
        // counted the skipped cards towards the chunk as well, the migrations in the repository are cut that way.
        for (int index = 0; index < cards.size(); index++) {
            SeasonDraftCardVO card = cards.get(index);
            if (card.getSkipReason() != null) continue;

            values.add("\t(" + escapeUuid(card.getOracleId()) + ", " + escapeString(card.getName()) + ", " + escapeString(card.getNormalizedName()) + ", " + addedAtValue + ")");
            if (index > 0 && index % CHUNK_SIZE == 0) {
                sql.append(cardBatch(values));
                values.clear();
            }
        }

        if (!values.isEmpty()) {
            sql.append(cardBatch(values));
        }

        return sql.toString();
    }

    /*
        CREATE TABLE IF NOT EXISTS public.card_season_data
        (
            id bigserial NOT NULL,
            season_id integer NOT NULL,
            card_oracle_id uuid NOT NULL,
            budget_points integer,
            legality legality,
            meta_share_standard numeric(4, 3),
            meta_share_pioneer numeric(4, 3),
            meta_share_modern numeric(4, 3),
            meta_share_legacy numeric(4, 3),
            meta_share_vintage numeric(4, 3),
            meta_share_pauper numeric(4, 3),
            banned_in mtg_format,
            vintage_restricted boolean,

            PRIMARY KEY (id)
        );
     */
    public static String insertCardSeasonData(int seasonId, List<SeasonDraftCardVO> cards) { // keep in sync with SeasonDraftRepository.commit
        List<SeasonDraftCardVO> sortedCards = new ArrayList<>(cards);
        sortedCards.sort(Comparator.comparing(SeasonDraftCardVO::getName));

        StringBuilder sql = new StringBuilder();
        List<String> values = new ArrayList<>(CHUNK_SIZE);
        for (int index = 0; index < sortedCards.size(); index++) {
            SeasonDraftCardVO card = sortedCards.get(index);
            if (card.getSkipReason() != null) continue;

            values.add("\t(" + seasonId
                    + ", " + escapeUuid(card.getOracleId())
                    + ", " + card.getBudgetPoints()
                    + ", '" + LEGALITY_CONVERTER.convertToDatabaseColumn(card.getLegality()) + "'::legality"
                    + ", " + metaShare(card.getMetaShareStandard())
                    + ", " + metaShare(card.getMetaSharePioneer())
                    + ", " + metaShare(card.getMetaShareModern())
                    + ", " + metaShare(card.getMetaShareLegacy())
                    + ", " + metaShare(card.getMetaShareVintage())
                    + ", " + metaShare(card.getMetaSharePauper())
                    + ", " + bannedIn(card.getBannedIn())
                    + ", " + (card.isVintageRestricted() ? "TRUE" : "FALSE") + ")");
            if (index > 0 && index % CHUNK_SIZE == 0) {
                sql.append(cardSeasonDataBatch(values));
                values.clear();
            }
        }

        if (!values.isEmpty()) {
            sql.append(cardSeasonDataBatch(values));
        }

        return sql.toString();
    }

    private static String cardBatch(List<String> values) {
        return "INSERT INTO public.card (oracle_id, \"name\", normalized_name, added_at)\n"
                + "VALUES\n    " + String.join(",\n", values) + "\n"
                + "ON CONFLICT (oracle_id) DO NOTHING;\n";
    }

    private static String cardSeasonDataBatch(List<String> values) {
        return "INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, meta_share_standard, meta_share_pioneer, meta_share_modern, meta_share_legacy, meta_share_vintage, meta_share_pauper, banned_in, vintage_restricted)\n"
                + "VALUES\n    " + String.join(",\n", values) + "\n"
                + "ON CONFLICT (season_id, card_oracle_id) DO UPDATE SET\n"
                + "    budget_points = EXCLUDED.budget_points,\n"
                + "    legality = EXCLUDED.legality,\n"
                + "    meta_share_standard = EXCLUDED.meta_share_standard,\n"
                + "    meta_share_pioneer = EXCLUDED.meta_share_pioneer,\n"
                + "    meta_share_modern = EXCLUDED.meta_share_modern,\n"
                + "    meta_share_legacy = EXCLUDED.meta_share_legacy,\n"
                + "    meta_share_vintage = EXCLUDED.meta_share_vintage,\n"
                + "    meta_share_pauper = EXCLUDED.meta_share_pauper,\n"
                + "    banned_in = EXCLUDED.banned_in,\n"
                + "    vintage_restricted = EXCLUDED.vintage_restricted;\n";
    }

    private static String metaShare(BigDecimal metaShare) {
        if (metaShare == null) return "NULL";

        return metaShare.toPlainString();
    }

    private static String bannedIn(MtgFormat mtgFormat) {
        if (mtgFormat == null) return "NULL";

        return "'" + MTG_FORMAT_CONVERTER.convertToDatabaseColumn(mtgFormat) + "'::mtg_format";
    }

    private static String escapeString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String escapeUuid(UUID oracleId) {
        return "'" + oracleId + "'::uuid";
    }
}

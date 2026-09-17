package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.model.values.CommittedSeasonCountsVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.model.values.SeasonVO;
import gg.casualchallenge.application.persistence.converters.LegalityConverter;
import gg.casualchallenge.application.persistence.converters.MtgFormatConverter;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component // not @Repository: the exception translation turns the IllegalStateExceptions below into data access exceptions, and the 409 becomes a 500
public class SeasonDraftRepository {

    private static final int BATCH_SIZE = 1000;

    private static final LegalityConverter LEGALITY_CONVERTER = new LegalityConverter();
    private static final MtgFormatConverter MTG_FORMAT_CONVERTER = new MtgFormatConverter();

    private static final String SELECT_DRAFT = "SELECT id, season_number, start_date, end_date, price_window_start, price_window_end, previous_season_id, previous_season_updated_at, mtgjson_date, meta_source, prepared_at, prepared_by, committed_at, committed_by, report FROM public.season_draft";

    private static final String INSERT_DRAFT_CARD = "INSERT INTO public.season_draft_card (season_draft_id, oracle_id, previous_oracle_id, name, normalized_name, budget_points, legality, meta_share_standard, meta_share_pioneer, meta_share_modern, meta_share_legacy, meta_share_vintage, meta_share_pauper, banned_in, vintage_restricted, is_new_card, skip_reason)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?::legality, ?, ?, ?, ?, ?, ?, ?::mtg_format, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public SeasonDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** There is only ever one draft to work on, so the old one is dropped. Committed drafts stay, they are the only record of a season start. */
    @Transactional
    public void replace(SeasonDraftVO draft, List<SeasonDraftCardVO> cards) {
        jdbcTemplate.update("DELETE FROM public.season_draft WHERE committed_at IS NULL"); // season_draft_card is cascaded
        // 32k card rows per season start add up. The newest committed draft keeps them so its migration files can still be downloaded, the older ones only keep their report.
        jdbcTemplate.update("DELETE FROM public.season_draft_card WHERE season_draft_id IN"
                + " (SELECT id FROM public.season_draft WHERE committed_at IS NOT NULL AND id <> (SELECT MAX(id) FROM public.season_draft WHERE committed_at IS NOT NULL))");

        Integer draftId = jdbcTemplate.queryForObject(
                "INSERT INTO public.season_draft (season_number, start_date, end_date, price_window_start, price_window_end, previous_season_id, previous_season_updated_at, mtgjson_date, meta_source, prepared_at, prepared_by, report)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Integer.class,
                draft.getSeasonNumber(),
                draft.getStartDate(),
                draft.getEndDate(),
                draft.getPriceWindowStart(),
                draft.getPriceWindowEnd(),
                draft.getPreviousSeasonId(),
                draft.getPreviousSeasonUpdatedAt(),
                draft.getMtgJsonDate(),
                draft.getMetaSource(),
                draft.getPreparedAt(),
                draft.getPreparedBy(),
                draft.getReport());

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (SeasonDraftCardVO card : cards) {
            batch.add(new Object[]{
                    draftId,
                    card.getOracleId(),
                    card.getPreviousOracleId(),
                    card.getName(),
                    card.getNormalizedName(),
                    card.getBudgetPoints(),
                    LEGALITY_CONVERTER.convertToDatabaseColumn(card.getLegality()),
                    card.getMetaShareStandard(),
                    card.getMetaSharePioneer(),
                    card.getMetaShareModern(),
                    card.getMetaShareLegacy(),
                    card.getMetaShareVintage(),
                    card.getMetaSharePauper(),
                    MTG_FORMAT_CONVERTER.convertToDatabaseColumn(card.getBannedIn()),
                    card.isVintageRestricted(),
                    card.isNewCard(),
                    card.getSkipReason()
            });
            if (batch.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(INSERT_DRAFT_CARD, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_DRAFT_CARD, batch);
        }
    }

    public SeasonDraftVO findDraft() {
        List<SeasonDraftVO> drafts = jdbcTemplate.query(SELECT_DRAFT + " ORDER BY id DESC LIMIT 1", SeasonDraftRepository::toDraftVO);
        if (drafts.isEmpty()) return null;
        return drafts.get(0);
    }

    public SeasonDraftVO findUncommittedDraft() {
        List<SeasonDraftVO> drafts = jdbcTemplate.query(SELECT_DRAFT + " WHERE committed_at IS NULL ORDER BY id DESC LIMIT 1", SeasonDraftRepository::toDraftVO);
        if (drafts.isEmpty()) return null;
        return drafts.get(0);
    }

    public List<SeasonDraftCardVO> findDraftCards(int draftId) {
        return jdbcTemplate.query(
                "SELECT oracle_id, previous_oracle_id, name, normalized_name, budget_points, legality, meta_share_standard, meta_share_pioneer, meta_share_modern, meta_share_legacy, meta_share_vintage, meta_share_pauper, banned_in, vintage_restricted, is_new_card, skip_reason" +
                        " FROM public.season_draft_card WHERE season_draft_id = ? ORDER BY id",
                SeasonDraftRepository::toDraftCardVO,
                draftId);
    }

    @Transactional
    public CommittedSeasonCountsVO commit(int draftId, LocalDateTime addedAt, LocalDateTime committedAt, String committedBy) {
        List<SeasonDraftVO> drafts = jdbcTemplate.query(SELECT_DRAFT + " WHERE id = ? FOR UPDATE", SeasonDraftRepository::toDraftVO, draftId);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("There is no season draft with id '" + draftId + "'.");
        }

        SeasonDraftVO draft = drafts.get(0);
        int seasonNumber = draft.getSeasonNumber();
        if (draft.getCommittedAt() != null) {
            throw new IllegalStateException("Draft for season " + seasonNumber + " was already committed.");
        }

        // The current season is the one with the latest start date, same as everywhere else in the application
        List<SeasonVO> currentSeasons = jdbcTemplate.query("SELECT id, season_number, start_date, end_date, updated_at FROM public.season ORDER BY start_date DESC LIMIT 1", SeasonDraftRepository::toSeasonVO);
        if (currentSeasons.isEmpty()) {
            throw new IllegalStateException("Draft for season " + seasonNumber + " has no season to continue.");
        }

        SeasonVO currentSeason = currentSeasons.get(0);
        if (seasonNumber != currentSeason.getSeasonNumber() + 1) {
            throw new IllegalStateException("Draft for season " + seasonNumber + " does not continue the current season " + currentSeason.getSeasonNumber() + ".");
        }
        if (draft.getPreviousSeasonId() != currentSeason.getId()) {
            throw new IllegalStateException("Draft for season " + seasonNumber + " was prepared against season " + draft.getPreviousSeasonId() + ", but the current season is " + currentSeason.getId() + ".");
        }

        Integer existingSeasons = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.season WHERE id = ? OR season_number = ?", Integer.class, seasonNumber, seasonNumber);
        if (existingSeasons != null && existingSeasons > 0) {
            throw new IllegalStateException("Season " + seasonNumber + " already exists.");
        }
        if (!Objects.equals(currentSeason.getUpdatedAt(), draft.getPreviousSeasonUpdatedAt())) {
            throw new IllegalStateException("Draft for season " + seasonNumber + " is stale, season " + currentSeason.getId() + " changed since the draft was prepared.");
        }

        List<Map<String, Object>> remaps = jdbcTemplate.queryForList("SELECT oracle_id, previous_oracle_id, name FROM public.season_draft_card WHERE season_draft_id = ? AND previous_oracle_id IS NOT NULL AND skip_reason IS NULL ORDER BY id", draftId);
        Set<Object> remappedOracleIds = new HashSet<>(remaps.size());
        for (Map<String, Object> remap : remaps) {
            Integer cardsToRemap = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.card WHERE oracle_id = ?", Integer.class, remap.get("previous_oracle_id"));
            if (cardsToRemap == null || cardsToRemap == 0) {
                throw new IllegalStateException("Card '" + remap.get("name") + "' is remapped from oracle id '" + remap.get("previous_oracle_id") + "', but no card has that oracle id.");
            }
            if (!remappedOracleIds.add(remap.get("oracle_id"))) {
                throw new IllegalStateException("Card '" + remap.get("name") + "' is remapped to oracle id '" + remap.get("oracle_id") + "', which another card of this draft claims as well.");
            }
            List<String> otherNames = jdbcTemplate.queryForList("SELECT name FROM public.card WHERE oracle_id = ? AND name <> ?", String.class, remap.get("oracle_id"), remap.get("name"));
            if (!otherNames.isEmpty()) {
                throw new IllegalStateException("Card '" + remap.get("name") + "' is remapped to oracle id '" + remap.get("oracle_id") + "', which already belongs to '" + otherNames.get(0) + "'.");
            }
        }

        List<Map<String, Object>> renames = jdbcTemplate.queryForList(
                "SELECT card.oracle_id, card.name AS previous_name, draft_card.name, draft_card.normalized_name" +
                        " FROM public.season_draft_card draft_card JOIN public.card ON card.oracle_id = COALESCE(draft_card.previous_oracle_id, draft_card.oracle_id)" +
                        " WHERE draft_card.season_draft_id = ? AND draft_card.skip_reason IS NULL" +
                        " AND (card.name <> draft_card.name OR card.normalized_name <> draft_card.normalized_name) ORDER BY draft_card.id",
                draftId);
        for (Map<String, Object> rename : renames) {
            List<String> otherNames = jdbcTemplate.queryForList("SELECT name FROM public.card WHERE (name = ? OR normalized_name = ?) AND oracle_id <> ?",
                    String.class, rename.get("name"), rename.get("normalized_name"), rename.get("oracle_id"));
            if (!otherNames.isEmpty()) {
                throw new IllegalStateException("Card '" + rename.get("previous_name") + "' would be renamed to '" + rename.get("name") + "', which already belongs to '" + otherNames.get(0) + "'.");
            }
        }

        // Step: close the previous season and open the new one
        jdbcTemplate.update("UPDATE public.season SET end_date = ?, updated_at = now() WHERE id = ?", draft.getStartDate().minusDays(1), draft.getPreviousSeasonId());
        jdbcTemplate.update("INSERT INTO public.season (id, season_number, start_date, end_date, updated_at) VALUES (?, ?, ?, ?, now())", seasonNumber, seasonNumber, draft.getStartDate(), draft.getEndDate());
        jdbcTemplate.execute("SELECT setval('season_id_seq', (SELECT MAX(id) FROM public.season))");

        // Step: cards that got a new oracle id keep their old rows
        for (Map<String, Object> remap : remaps) {
            // card_season_data references card.oracle_id without ON UPDATE CASCADE and the foreign key is checked at the end of the statement --> both updates have to be one statement
            jdbcTemplate.update("WITH remapped_card AS (UPDATE public.card SET oracle_id = ? WHERE oracle_id = ?) UPDATE public.card_season_data SET card_oracle_id = ? WHERE card_oracle_id = ?",
                    remap.get("oracle_id"),
                    remap.get("previous_oracle_id"),
                    remap.get("oracle_id"),
                    remap.get("previous_oracle_id"));
        }

        // Step: MTGJSON renames cards from time to time, the card table has to follow or the API stops finding them
        // keep in sync with SeasonMigrationSql.addSeason
        int updatedCardNames = jdbcTemplate.update(
                "UPDATE public.card SET name = draft_card.name, normalized_name = draft_card.normalized_name" +
                        " FROM public.season_draft_card draft_card" +
                        " WHERE card.oracle_id = draft_card.oracle_id AND draft_card.season_draft_id = ? AND draft_card.skip_reason IS NULL" +
                        " AND (card.name <> draft_card.name OR card.normalized_name <> draft_card.normalized_name)",
                draftId);

        int insertedCards = jdbcTemplate.update( // keep in sync with SeasonMigrationSql.insertCards
                "INSERT INTO public.card (oracle_id, name, normalized_name, added_at)" +
                        " SELECT oracle_id, name, normalized_name, ? FROM public.season_draft_card WHERE season_draft_id = ? AND is_new_card AND skip_reason IS NULL" +
                        " ON CONFLICT (oracle_id) DO NOTHING",
                addedAt,
                draftId);

        int upsertedCardSeasonData = jdbcTemplate.update( // keep in sync with SeasonMigrationSql.insertCardSeasonData
                "INSERT INTO public.card_season_data (season_id, card_oracle_id, budget_points, legality, meta_share_standard, meta_share_pioneer, meta_share_modern, meta_share_legacy, meta_share_vintage, meta_share_pauper, banned_in, vintage_restricted)" +
                        " SELECT ?, oracle_id, budget_points, legality, meta_share_standard, meta_share_pioneer, meta_share_modern, meta_share_legacy, meta_share_vintage, meta_share_pauper, banned_in, vintage_restricted" +
                        " FROM public.season_draft_card WHERE season_draft_id = ? AND skip_reason IS NULL" +
                        " ON CONFLICT (season_id, card_oracle_id) DO UPDATE SET" +
                        " budget_points = EXCLUDED.budget_points," +
                        " legality = EXCLUDED.legality," +
                        " meta_share_standard = EXCLUDED.meta_share_standard," +
                        " meta_share_pioneer = EXCLUDED.meta_share_pioneer," +
                        " meta_share_modern = EXCLUDED.meta_share_modern," +
                        " meta_share_legacy = EXCLUDED.meta_share_legacy," +
                        " meta_share_vintage = EXCLUDED.meta_share_vintage," +
                        " meta_share_pauper = EXCLUDED.meta_share_pauper," +
                        " banned_in = EXCLUDED.banned_in," +
                        " vintage_restricted = EXCLUDED.vintage_restricted",
                seasonNumber,
                draftId);

        // Not now(): the database runs in local time while prepared_at is UTC, and the two of them name the migration files
        jdbcTemplate.update("UPDATE public.season_draft SET committed_at = ?, committed_by = ? WHERE id = ?", committedAt, committedBy, draftId);

        return new CommittedSeasonCountsVO(remaps.size(), updatedCardNames, insertedCards, upsertedCardSeasonData);
    }

    @Transactional
    public void discard() {
        jdbcTemplate.update("DELETE FROM public.season_draft WHERE committed_at IS NULL"); // season_draft_card is cascaded
    }

    private static SeasonVO toSeasonVO(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SeasonVO(
                resultSet.getInt("id"),
                resultSet.getInt("season_number"),
                resultSet.getObject("start_date", LocalDate.class),
                resultSet.getObject("end_date", LocalDate.class),
                resultSet.getObject("updated_at", LocalDateTime.class));
    }

    private static SeasonDraftVO toDraftVO(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SeasonDraftVO(
                resultSet.getInt("id"),
                resultSet.getInt("season_number"),
                resultSet.getObject("start_date", LocalDate.class),
                resultSet.getObject("end_date", LocalDate.class),
                resultSet.getObject("price_window_start", LocalDate.class),
                resultSet.getObject("price_window_end", LocalDate.class),
                resultSet.getInt("previous_season_id"),
                resultSet.getObject("previous_season_updated_at", LocalDateTime.class),
                resultSet.getString("mtgjson_date"),
                resultSet.getString("meta_source"),
                resultSet.getObject("prepared_at", LocalDateTime.class),
                resultSet.getString("prepared_by"),
                resultSet.getObject("committed_at", LocalDateTime.class),
                resultSet.getString("committed_by"),
                resultSet.getString("report"));
    }

    private static SeasonDraftCardVO toDraftCardVO(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SeasonDraftCardVO(
                resultSet.getObject("oracle_id", UUID.class),
                resultSet.getObject("previous_oracle_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("normalized_name"),
                resultSet.getObject("budget_points", Integer.class),
                LEGALITY_CONVERTER.convertToEntityAttribute(resultSet.getString("legality")),
                resultSet.getObject("meta_share_standard", BigDecimal.class),
                resultSet.getObject("meta_share_pioneer", BigDecimal.class),
                resultSet.getObject("meta_share_modern", BigDecimal.class),
                resultSet.getObject("meta_share_legacy", BigDecimal.class),
                resultSet.getObject("meta_share_vintage", BigDecimal.class),
                resultSet.getObject("meta_share_pauper", BigDecimal.class),
                MTG_FORMAT_CONVERTER.convertToEntityAttribute(resultSet.getString("banned_in")),
                resultSet.getBoolean("vintage_restricted"),
                resultSet.getBoolean("is_new_card"),
                resultSet.getString("skip_reason"));
    }
}

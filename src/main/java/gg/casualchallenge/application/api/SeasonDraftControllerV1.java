package gg.casualchallenge.application.api;

import gg.casualchallenge.application.api.datamodel.CommittedSeasonResponse;
import gg.casualchallenge.application.api.datamodel.SeasonDraftReportResponse;
import gg.casualchallenge.application.dataprocessor.SeasonDraftService;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFile;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFileVO;
import gg.casualchallenge.application.model.mapper.CommittedSeasonMapper;
import gg.casualchallenge.application.model.mapper.SeasonDraftReportMapper;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/admin/v1")
@Tag(name = "Season draft", description = "The stored result of a preparation. Read the report, download the three migrations, then commit the draft or throw it away. There is only ever one open draft, a new preparation replaces it. Committed ones stay in the table, they are the only record of a season start.")
public class SeasonDraftControllerV1 {

    private final SeasonDraftService seasonDraftService;

    public SeasonDraftControllerV1(SeasonDraftService seasonDraftService) {
        this.seasonDraftService = seasonDraftService;
    }

    @GetMapping(path = "/season/draft")
    @Operation(
            summary = "Read the review report of the draft",
            description = "Everything you need to decide whether the draft is good enough to commit: the dates, the counts, the ban and budget point changes, the cards that got left out and the three Scryfall lists. Without a draft there is nothing to read and you get a 404."
    )
    public SeasonDraftReportResponse getSeasonDraft() {
        SeasonDraftReportVO report = this.seasonDraftService.report();
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season draft.");
        }

        return SeasonDraftReportMapper.INSTANCE.toResponse(report);
    }

    @GetMapping(path = "/season/draft/sql/{part}", produces = "text/plain;charset=utf-8")
    @Operation(
            summary = "Download one of the three season migrations",
            description = "Comes as an attachment: 20260913_1042_00_add_season_21.sql, _01_insert_cards.sql, _02_insert_card_season_data_for_season_21.sql. All three share the timestamp, so 00_add_season is applied before the data it needs. 404 for a part that doesn't exist or when there is no draft, 409 for a draft an older build prepared."
    )
    public ResponseEntity<String> getSeasonDraftSql(
            @Parameter(description = "Which of the three migrations to download.", schema = @Schema(allowableValues = {"00_add_season", "01_insert_cards", "02_insert_card_season_data"}))
            @PathVariable("part") String part
    ) {
        SeasonSqlFile seasonSqlFile = SeasonSqlFile.fromPart(part);
        if (seasonSqlFile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season migration named '" + part + "'.");
        }

        SeasonSqlFileVO sqlFile;
        try {
            sqlFile = this.seasonDraftService.exportSql(seasonSqlFile);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        if (sqlFile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season draft.");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sqlFile.getFileName() + "\"")
                .body(sqlFile.getContent());
    }

    @PostMapping(path = "/season/commit")
    @Operation(
            summary = "Commit the draft",
            description = "Writes the season, the new cards and their season data in one transaction, remaps the oracle ids that changed and reloads the card cache. Answers with the facts for the season announcement. 409 when there is no draft, when it was committed already or when the current season was touched since the preparation ran - prepare again in that case."
    )
    public CommittedSeasonResponse commitSeason() {
        try {
            return CommittedSeasonMapper.INSTANCE.toResponse(this.seasonDraftService.commit());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping(path = "/season/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Throw the draft away",
            description = "Nothing that is live gets touched, the draft row is simply gone. Refuses with 409 if there is no draft, or if it was committed already - a committed draft stays, it is the only record of a season start."
    )
    public void discardSeasonDraft() {
        try {
            this.seasonDraftService.discard();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}

package gg.casualchallenge.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.dataprocessor.SeasonDraftService;
import gg.casualchallenge.application.dataprocessor.SeasonPreparationService;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFile;
import gg.casualchallenge.application.dataprocessor.model.SeasonSqlFileVO;
import gg.casualchallenge.application.model.values.CommittedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonPreparationJobVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;


@Hidden
@RestController
@RequestMapping("/admin/v1")
public class AdminControllerV1 {

    private final SeasonPreparationService seasonPreparationService;
    private final SeasonDraftService seasonDraftService;
    private final CasualChallengeService casualChallengeService;
    private final ObjectMapper objectMapper;

    public AdminControllerV1(
            SeasonPreparationService seasonPreparationService,
            SeasonDraftService seasonDraftService,
            CasualChallengeService casualChallengeService,
            ObjectMapper objectMapper
    ) {
        this.seasonPreparationService = seasonPreparationService;
        this.seasonDraftService = seasonDraftService;
        this.casualChallengeService = casualChallengeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/season/prepare")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SeasonPreparationJobVO prepareSeason(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) LocalDate priceWindowStart,
            @RequestParam(required = false) LocalDate priceWindowEnd,
            @RequestParam(required = false) String metaSource,
            @RequestParam(required = false) MultipartFile bans,
            @RequestParam(required = false) MultipartFile extendedBans
    ) {
        SeasonPreparationRequestVO request = toRequest(startDate, endDate, priceWindowStart, priceWindowEnd, metaSource, bans, extendedBans);
        try {
            return this.seasonPreparationService.prepare(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping(path = "/season/prepare/status")
    public SeasonPreparationJobVO getSeasonPreparationStatus() {
        return this.seasonPreparationService.getJob();
    }

    @PostMapping(path = "/season/prepare/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSeasonPreparation() {
        this.seasonPreparationService.cancel();
    }

    @GetMapping(path = "/season/draft")
    public SeasonDraftReportVO getSeasonDraft() {
        SeasonDraftReportVO report = this.seasonDraftService.report(); // the report is the JSON that sits in season_draft --> nothing to map it to
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season draft.");
        }

        return report;
    }

    @GetMapping(path = "/season/draft/sql/{part}", produces = "text/plain;charset=utf-8")
    public ResponseEntity<String> getSeasonDraftSql(
            @PathVariable("part") String part
    ) {
        SeasonSqlFileVO sqlFile = this.seasonDraftService.exportSql(toSqlFile(part));
        if (sqlFile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season draft.");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sqlFile.getFileName() + "\"")
                .body(sqlFile.getContent());
    }

    @PostMapping(path = "/season/commit")
    public CommittedSeasonVO commitSeason() {
        try {
            return this.seasonDraftService.commit();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping(path = "/season/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardSeasonDraft() {
        try {
            this.seasonDraftService.discard();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping(path = "/cards/reload")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reloadCards() {
        this.casualChallengeService.preloadCards();
    }


    private SeasonPreparationRequestVO toRequest(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate priceWindowStart,
            LocalDate priceWindowEnd,
            String metaSource,
            MultipartFile bans,
            MultipartFile extendedBans
    ) {
        LocalDate seasonStart = startDate != null ? startDate : LocalDate.now(Constants.TIMEZONE);
        SeasonPreparationRequestVO defaultRequest = this.seasonPreparationService.defaultRequest(seasonStart);
        MetaShareSource metaShareSource = toMetaShareSource(metaSource, defaultRequest.getMetaSource());
        PriceWindowVO priceWindow = new PriceWindowVO(
                priceWindowStart != null ? priceWindowStart : defaultRequest.getPriceWindow().getStart(),
                priceWindowEnd != null ? priceWindowEnd : defaultRequest.getPriceWindow().getEnd()
        );

        return new SeasonPreparationRequestVO(
                seasonStart,
                endDate != null ? endDate : defaultRequest.getEndDate(),
                priceWindow,
                metaShareSource,
                metaShareSource == MetaShareSource.FILES ? readBanFile(bans, "bans") : null,
                metaShareSource == MetaShareSource.FILES ? readBanFile(extendedBans, "extendedBans") : null
        );
    }

    private List<BanDTO> readBanFile(MultipartFile banFile, String parameterName) {
        if (banFile == null || banFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The meta source 'files' needs an uploaded '" + parameterName + "' file.");
        }

        try {
            return Arrays.asList(this.objectMapper.readValue(banFile.getInputStream(), BanDTO[].class));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couldn't read the uploaded '" + parameterName + "' file: " + e.getMessage());
        }
    }

    private static MetaShareSource toMetaShareSource(String metaSource, MetaShareSource defaultSource) {
        if (metaSource == null) return defaultSource;

        for (MetaShareSource metaShareSource : MetaShareSource.values()) {
            if (metaShareSource.name().equalsIgnoreCase(metaSource)) return metaShareSource;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown meta source '" + metaSource + "'.");
    }

    private static SeasonSqlFile toSqlFile(String part) {
        for (SeasonSqlFile seasonSqlFile : SeasonSqlFile.values()) {
            if (seasonSqlFile.getPart().equals(part)) return seasonSqlFile;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season migration named '" + part + "'.");
    }
}

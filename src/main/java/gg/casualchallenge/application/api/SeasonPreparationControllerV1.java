package gg.casualchallenge.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.api.datamodel.SeasonPreparationStatusResponse;
import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.dataprocessor.SeasonPreparationService;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.model.mapper.SeasonPreparationStatusMapper;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/admin/v1")
@Tag(name = "Season preparation", description = "Preparing a season calculates the next one from scratch - both MTGJSON dumps, the prices of the whole price window, the tournament staples - so it runs in the background. A draft is the finished next season, parked in the season_draft table; nothing live changes before the commit.\n\n"
        + "- POST here to start it\n"
        + "- GET here until the state is DONE\n"
        + "- GET /admin/v1/season/draft for the report\n"
        + "- download the three sql files\n"
        + "- POST /admin/v1/season/commit")
public class SeasonPreparationControllerV1 {

    private final SeasonPreparationService seasonPreparationService;
    private final ObjectMapper objectMapper;

    public SeasonPreparationControllerV1(
            SeasonPreparationService seasonPreparationService,
            ObjectMapper objectMapper
    ) {
        this.seasonPreparationService = seasonPreparationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/season/preparation", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE}) // multipart for the ban files, */* so a bodyless POST still works
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Start preparing the next season",
            description = "Answers right away with the status, the work happens in the background. 409 while another preparation is still running. 400 when the dates don't work out or metaSource=files comes without the two files. Every parameter is optional, the defaults are with the parameters."
    )
    public SeasonPreparationStatusResponse prepareSeason(
            @Parameter(description = "First day of the new season. Defaults to today (UTC).")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Last day of the new season. Defaults to the end of the current season plus casual-challenge.season.length-in-weeks (10 weeks).")
            @RequestParam(required = false) LocalDate endDate,
            @Parameter(description = "First day whose prices count. Defaults to casual-challenge.season.price-window-days (70) before the start date.")
            @RequestParam(required = false) LocalDate priceWindowStart,
            @Parameter(description = "Exclusive end of the price window. Defaults to the start date.")
            @RequestParam(required = false) LocalDate priceWindowEnd,
            @Parameter(description = "Where the tournament staples come from - mtggoldfish, mtgtop8 or files. Defaults to mtggoldfish.")
            @RequestParam(required = false) MetaShareSource metaSource,
            @Parameter(description = "bans.json, only used with metaSource=files. A JSON array of {name, formats}, the shape /legacy/season/{season}/bans.json answers with.")
            @RequestParam(required = false) MultipartFile bans,
            @Parameter(description = "extended-bans.json, only used with metaSource=files. Same shape as bans.")
            @RequestParam(required = false) MultipartFile extendedBans
    ) {
        SeasonPreparationRequestVO request = new SeasonPreparationRequestVO(
                startDate,
                endDate,
                new PriceWindowVO(priceWindowStart, priceWindowEnd),
                metaSource,
                metaSource == MetaShareSource.FILES ? readBanFile(bans, "bans") : null,
                metaSource == MetaShareSource.FILES ? readBanFile(extendedBans, "extendedBans") : null
        );
        try {
            return SeasonPreparationStatusMapper.INSTANCE.toResponse(this.seasonPreparationService.prepare(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping(path = "/season/preparation")
    @Operation(
            summary = "Status of the preparation",
            description = "IDLE until the first one was started, after that RUNNING, DONE, FAILED or CANCELLED, with the step it is on and the error message if it failed."
    )
    public SeasonPreparationStatusResponse getSeasonPreparationStatus() {
        return SeasonPreparationStatusMapper.INSTANCE.toResponse(this.seasonPreparationService.status());
    }

    @DeleteMapping(path = "/season/preparation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancel the running preparation",
            description = "The preparation only gives up between steps, so this is not instant. Nothing lands in the database --> nothing to clean up, the downloaded MTGJSON files stay in the archive. Answers 204 even when nothing is running."
    )
    public void cancelSeasonPreparation() {
        this.seasonPreparationService.cancel();
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    private void handleUnknownParameterValue(MethodArgumentTypeMismatchException e, HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.BAD_REQUEST.value(), "'" + e.getValue() + "' is not a valid value for '" + e.getName() + "'."); // the exception's own message carries the class name of the enum
    }

    private List<BanDTO> readBanFile(MultipartFile banFile, String parameterName) {
        if (banFile == null || banFile.isEmpty()) return null;

        try {
            return Arrays.asList(this.objectMapper.readValue(banFile.getInputStream(), BanDTO[].class));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couldn't read the uploaded '" + parameterName + "' file: " + e.getMessage());
        }
    }
}

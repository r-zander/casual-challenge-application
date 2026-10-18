package gg.casualchallenge.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.api.datamodel.RebuiltPriceDetailsResponse;
import gg.casualchallenge.application.api.datamodel.SeasonPreparationStatusResponse;
import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.dataprocessor.SeasonPreparationService;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.model.mapper.RebuiltPriceDetailsMapper;
import gg.casualchallenge.application.model.mapper.SeasonPreparationStatusMapper;
import gg.casualchallenge.application.model.values.RebuiltPriceDetailsVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/admin/v1")
@Tag(
        name = "Season preparation",
        description = """
                Preparing a season calculates the next one from scratch \
                - both MTGJSON dumps, the prices of the whole price window, the tournament staples \
                - so it runs in the background. \
                A draft is the finished next season, parked in the season_draft table; \
                nothing live changes before the commit.

                - POST here to start it
                - GET here until the state is DONE
                - GET /admin/v1/season/draft for the report
                - download the three sql files
                - POST /admin/v1/season/commit
                - DELETE here to give up on a running preparation, DELETE the draft to throw a finished one away"""
)
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

    // Multipart for the ban files, */* so a bodyless POST still works
    @PostMapping(path = "/season/preparation", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Start preparing the next season",
            description = """
                    Answers right away with the status, the work happens in the background.

                    Every parameter is optional, the defaults are with the parameters.
                    """
    )
    @ApiResponse(
            responseCode = "400",
            description = "The dates don't work out, `metaSource` is none of the three"
                    + " or `metaSource=files` comes without the two files",
            content = @Content
    )
    @ApiResponse(
            responseCode = "409",
            description = "Another preparation or a rebuild of price details is still running",
            content = @Content
    )
    public SeasonPreparationStatusResponse prepareSeason(
            @Parameter(description = "First day of the new season, today (UTC) if you leave it out.")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Last day of the new season."
                    + " Defaults to the end of the current season"
                    + " plus casual-challenge.season.length-in-weeks (10 weeks).")
            @RequestParam(required = false) LocalDate endDate,
            @Parameter(description = "First day whose prices count."
                    + " Defaults to casual-challenge.season.price-window-days (70) before the start date.")
            @RequestParam(required = false) LocalDate priceWindowStart,
            @Parameter(description = "Exclusive end of the price window, the start date if you leave it out.")
            @RequestParam(required = false) LocalDate priceWindowEnd,
            @Parameter(description = "Where the tournament staples come from - mtggoldfish, mtgtop8 or files."
                    + " Defaults to mtggoldfish.")
            @RequestParam(required = false) MetaShareSource metaSource,
            @Parameter(description = "bans.json, only used with `metaSource=files`."
                    + " A JSON array of {name, formats}, the shape /legacy/season/{season}/bans.json answers with.")
            @RequestParam(required = false) MultipartFile bans,
            @Parameter(description = "extended-bans.json, only used with `metaSource=files`. Same shape as `bans`.")
            @RequestParam(required = false) MultipartFile extendedBans,
            Principal principal
    ) {
        List<BanDTO> banDTOs = null;
        List<BanDTO> extendedBanDTOs = null;
        if (metaSource == MetaShareSource.FILES) {
            banDTOs = readBanFile(bans, "bans");
            extendedBanDTOs = readBanFile(extendedBans, "extendedBans");
        }

        SeasonPreparationRequestVO request = new SeasonPreparationRequestVO(
                startDate,
                endDate,
                new PriceWindowVO(priceWindowStart, priceWindowEnd),
                metaSource,
                banDTOs,
                extendedBanDTOs,
                principal.getName()
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
            description = """
                    IDLE until the first one was started, after that RUNNING, DONE, FAILED or CANCELLED, \
                    with the step it is on (`stepId` and `stepNumber` name it, `step` is the sentence) \
                    and the error message if it failed.
                    """
    )
    @ApiResponse(responseCode = "200")
    public SeasonPreparationStatusResponse getSeasonPreparationStatus() {
        return SeasonPreparationStatusMapper.INSTANCE.toResponse(this.seasonPreparationService.status());
    }

    @DeleteMapping(path = "/season/preparation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancel the running preparation",
            description = """
                    The preparation only gives up between steps, so this is not instant. \
                    Nothing lands in the database, so there is nothing to clean up - \
                    the downloaded MTGJSON files stay in the archive.
                    """
    )
    @ApiResponse(responseCode = "204", description = "Also when nothing is running")
    public void cancelSeasonPreparation() {
        this.seasonPreparationService.cancel();
    }

    @PostMapping(path = "/season/{seasonNumber}/price-details")
    @Operation(
            summary = "Rebuild the price details of a season",
            description = """
                    Prices the season again from the MTGJSON files its preparation left in the archive \
                    and replaces all of the season's price details with the result, \
                    so it only works while the archive is still there.

                    Synchronous and takes a few minutes - a proxy may cut the request off before the answer arrives, \
                    the rebuild finishes anyway and its log line has the same numbers.

                    - `mismatches` are cards priced differently than their stored budget points, \
                    `pricedBy` says by which market
                    - `unmatchedCards` are the ones with budget points but no card in the archive
                    - `adjustedExchangeRate` converts the tcgplayer prices of the rebuilt details, \
                    `committedAdjustedExchangeRate` is the one the season was committed with \
                    (null for a season without a committed draft)
                    """
    )
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "404", description = "The season doesn't exist or has no archive", content = @Content)
    @ApiResponse(
            responseCode = "409",
            description = "A preparation or another rebuild is running,"
                    + " or the archive holds another run of the season than the one that was committed",
            content = @Content
    )
    public RebuiltPriceDetailsResponse rebuildPriceDetails(
            @Parameter(description = "The season to rebuild, the number not the id - they happen to be the same.")
            @PathVariable("seasonNumber") int seasonNumber
    ) {
        RebuiltPriceDetailsVO rebuiltPriceDetails;
        try {
            rebuiltPriceDetails = this.seasonPreparationService.rebuildPriceDetails(seasonNumber);
        } catch (IllegalArgumentException e) {
            // The archive or a file in it is missing
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        if (rebuiltPriceDetails == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season " + seasonNumber + ".");
        }

        return RebuiltPriceDetailsMapper.INSTANCE.toResponse(rebuiltPriceDetails);
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    private void handleUnknownParameterValue(
            MethodArgumentTypeMismatchException e,
            HttpServletResponse response
    ) throws IOException {
        // The exception's own message carries the class name of the enum
        response.sendError(
                HttpStatus.BAD_REQUEST.value(),
                "'" + e.getValue() + "' is not a valid value for '" + e.getName() + "'."
        );
    }

    private List<BanDTO> readBanFile(MultipartFile banFile, String parameterName) {
        if (banFile == null) return null;
        if (banFile.isEmpty()) return null;

        try {
            return Arrays.asList(this.objectMapper.readValue(banFile.getInputStream(), BanDTO[].class));
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Couldn't read the uploaded '" + parameterName + "' file: " + e.getMessage()
            );
        }
    }
}

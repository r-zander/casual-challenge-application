package gg.casualchallenge.application.api;

import gg.casualchallenge.application.api.datamodel.RemovedSeasonResponse;
import gg.casualchallenge.application.api.datamodel.SeasonRemovalPreviewResponse;
import gg.casualchallenge.application.api.datamodel.SeasonRemovalRequest;
import gg.casualchallenge.application.dataprocessor.SeasonRemovalService;
import gg.casualchallenge.application.model.mapper.SeasonRemovalMapper;
import gg.casualchallenge.application.model.values.SeasonRemovalPreviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;


@RestController
@RequestMapping("/admin/v1")
@Tag(name = "Season removal", description = "Takes a season start back, for the rehearsal before the real one. It undoes exactly what the commit of that season's draft did: the season data, the cards it brought, the renames, the oracle id remaps and the end date of the season below it.\n\n"
        + "Once a migration for the season has run on this database it stays, whatever else you do - the season is part of the repository by then and the next deployment would write it straight back. Same for anything the season wizard did not commit itself, and for anything but the newest season.\n\n"
        + "- GET first, it says what would go and what stands in the way\n"
        + "- POST to do it")
public class SeasonRemovalControllerV1 {

    private final SeasonRemovalService seasonRemovalService;

    public SeasonRemovalControllerV1(SeasonRemovalService seasonRemovalService) {
        this.seasonRemovalService = seasonRemovalService;
    }

    @GetMapping(path = "/season/{seasonNumber}/removal")
    @Operation(
            summary = "What removing this season would do",
            description = "Changes nothing. removable says whether it can go at all, refusals says why not. The counts are the rows that would disappear, plus the season and end date that come back. A pullRequestUrl means the migrations are on GitHub and a token has to come with the removal. 404 for a season that doesn't exist."
    )
    public SeasonRemovalPreviewResponse getSeasonRemoval(
            @Parameter(description = "The season to look at, the number not the id - they happen to be the same.") @PathVariable("seasonNumber") int seasonNumber
    ) {
        SeasonRemovalPreviewVO preview = this.seasonRemovalService.preview(seasonNumber);
        if (preview == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season " + seasonNumber + ".");
        }

        return SeasonRemovalMapper.INSTANCE.toResponse(preview);
    }

    @PostMapping(path = "/season/{seasonNumber}/removal")
    @Operation(
            summary = "Remove the season",
            description = "The season, its card season data and the cards nothing else needs any more are gone, the season below it gets its end date back and the card cache is reloaded. The draft stays with removedAt and removedBy on it, it is the only record of the season start. The season's folder in the archive goes as well. 409 with the reasons when it can't go, 404 when the season doesn't exist."
    )
    public RemovedSeasonResponse removeSeason(
            @Parameter(description = "The season to remove. Has to be the current one.") @PathVariable("seasonNumber") int seasonNumber,
            @RequestBody(required = false) SeasonRemovalRequest request,
            Principal principal
    ) {
        if (!this.seasonRemovalService.exists(seasonNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season " + seasonNumber + ".");
        }

        try {
            return SeasonRemovalMapper.INSTANCE.toResponse(
                    this.seasonRemovalService.remove(seasonNumber, principal.getName(), request != null ? request.getGithubToken() : null));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}

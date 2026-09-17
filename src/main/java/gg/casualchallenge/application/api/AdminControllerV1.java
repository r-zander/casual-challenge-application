package gg.casualchallenge.application.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/admin/v1")
@Tag(name = "Admin", description = "The admin bits and pieces that have nothing to do with starting a season.")
public class AdminControllerV1 {

    private final CasualChallengeService casualChallengeService;

    public AdminControllerV1(CasualChallengeService casualChallengeService) {
        this.casualChallengeService = casualChallengeService;
    }

    @PostMapping(path = "/cards/reload")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Reload the card cache",
            description = "The cache is built at startup and again after every commit. This is for the case where a commit got through but the reload behind it didn't."
    )
    public void reloadCards() {
        this.casualChallengeService.preloadCards();
    }
}

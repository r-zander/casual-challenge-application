package gg.casualchallenge.application.api;

import gg.casualchallenge.application.model.response.CardsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static gg.casualchallenge.library.persistence.ParameterUtil.getPresenceBoolean;


@RestController
@RequestMapping("/v1")
public class ApiControllerV1 {

    private final CasualChallengeService casualChallengeService;

    public ApiControllerV1(CasualChallengeService casualChallengeService) {
        this.casualChallengeService = casualChallengeService;
    }

    @GetMapping(path = "/cards")
    public CardsResponse getCards(
            @RequestParam String names,
            @RequestParam(required = false) String displayExtended
    ) {
        List<String> nameList = Arrays.stream(names.split(";"))
                .filter(name -> !name.isBlank())
                .limit(100)
                .toList();
        if (nameList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The 'names' parameter is required and must contain at least one card name.");
        }
        return CardsResponseMapper.INSTANCE.toResponse(
                this.casualChallengeService.getCardData(null, nameList, getPresenceBoolean(displayExtended))
        );
    }
}

package gg.casualchallenge.application.api;

import gg.casualchallenge.application.model.response.CardsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/api/v1")
public class ApiControllerV1 {

    private final CasualChallengeService casualChallengeService;

    public ApiControllerV1(CasualChallengeService casualChallengeService) {
        this.casualChallengeService = casualChallengeService;
    }

    @GetMapping(path = "/cards")
    public CardsResponse getCards(
            @RequestParam(required = false, defaultValue = "") String names,
            @RequestParam(required = false, defaultValue = "false") boolean displayExtended
    ) {
        List<String> nameList = Arrays.stream(names.split(";"))
                .filter(name -> !name.isBlank())
                .toList();
        return CardsResponseMapper.INSTANCE.toResponse(this.casualChallengeService.getCardData(null, nameList, displayExtended));
    }
}

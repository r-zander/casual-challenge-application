package gg.casualchallenge.application;

import gg.casualchallenge.application.mapper.CardMapper;
import gg.casualchallenge.application.model.response.CardDTO;
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
    public List<CardDTO> getCards(@RequestParam(required = false, defaultValue = "") String names) {
        List<String> nameList = Arrays.stream(names.split(","))
                .filter(name -> !name.isBlank())
                .toList();
        return CardMapper.INSTANCE.toDTOList(this.casualChallengeService.findCards(nameList));
    }
}

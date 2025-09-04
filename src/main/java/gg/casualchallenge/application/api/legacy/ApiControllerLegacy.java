package gg.casualchallenge.application.api.legacy;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.mapper.BansResponseMapper;
import gg.casualchallenge.application.api.legacy.mapper.CardPricesResponseMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Hidden
@RestController
@RequestMapping("/legacy")
public class ApiControllerLegacy {

    private final CasualChallengeLegacyService casualChallengeLegacyService;

    public ApiControllerLegacy(CasualChallengeLegacyService casualChallengeLegacyService) {
        this.casualChallengeLegacyService = casualChallengeLegacyService;
    }

    @GetMapping(path = "/season/{season}/bans")
    public List<BanDTO> getBans(
            @PathVariable("season") @Min(0) int seasonNumber
    ) {
        return BansResponseMapper.INSTANCE.toResponse(
                this.casualChallengeLegacyService.findAllBannedCards(seasonNumber)
        );
    }

    @GetMapping(path = "/season/{season}/extended-bans")
    public List<BanDTO> getExtendedBans(
            @PathVariable("season") @Min(0) int seasonNumber
    ) {
        return BansResponseMapper.INSTANCE.toResponse(
                this.casualChallengeLegacyService.findAllExtendedBannedCards(seasonNumber)
        );
    }

    @GetMapping(path = "/season/{season}/card-prices")
    public Map<String, Integer> getCardPrices(
            @PathVariable("season") @Min(0) int seasonNumber
    ) {
        return CardPricesResponseMapper.INSTANCE.toResponse(
                this.casualChallengeLegacyService.findAllCardsWithBudgetPoints(seasonNumber)
        );
    }
}

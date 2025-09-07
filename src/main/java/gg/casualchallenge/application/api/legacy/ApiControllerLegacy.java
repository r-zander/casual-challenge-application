package gg.casualchallenge.application.api.legacy;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.datamodel.SeasonInfoDTO;
import gg.casualchallenge.application.api.legacy.datamodel.SeasonUrlsDTO;
import gg.casualchallenge.application.api.legacy.mapper.BansResponseMapper;
import gg.casualchallenge.application.api.legacy.mapper.CardPricesResponseMapper;
import gg.casualchallenge.application.api.legacy.mapper.SeasonInfoMapper;
import gg.casualchallenge.application.model.values.SeasonVO;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Hidden
@RestController
@RequestMapping(ApiControllerLegacy.ROOT_PATH)
public class ApiControllerLegacy {

    public static final String ROOT_PATH = "/legacy";

    private static final String PATH_BANS = "/season/{season}/bans.json";
    private static final String PATH_EXTENDED_BANS = "/season/{season}/extended-bans.json";
    private static final String PATH_CARD_PRICES = "/season/{season}/card-prices.json";

    private final CasualChallengeLegacyService casualChallengeLegacyService;

    public ApiControllerLegacy(CasualChallengeLegacyService casualChallengeLegacyService) {
        this.casualChallengeLegacyService = casualChallengeLegacyService;
    }

    @GetMapping(path = "/season/current")
    public SeasonInfoDTO getCurrentSeasonInfo() {
        SeasonVO currentSeason = this.casualChallengeLegacyService.getCurrentSeason();
        String bansUrl = UriComponentsBuilder.fromPath(ROOT_PATH + PATH_BANS)
                .buildAndExpand(currentSeason.getSeasonNumber())
                .toUriString();
        String extendedBansUrl = UriComponentsBuilder.fromPath(ROOT_PATH + PATH_EXTENDED_BANS)
                .buildAndExpand(currentSeason.getSeasonNumber())
                .toUriString();
        String cardPricesUrl = UriComponentsBuilder.fromPath(ROOT_PATH + PATH_CARD_PRICES)
                .buildAndExpand(currentSeason.getSeasonNumber())
                .toUriString();

        return SeasonInfoMapper.INSTANCE.toResponse(
                currentSeason,
                new SeasonUrlsDTO(bansUrl, extendedBansUrl, cardPricesUrl)
        );
    }

    @GetMapping(path = PATH_BANS)
    public List<BanDTO> getBans(
            @PathVariable("season") @Min(0) int seasonNumber
    ) {
        return BansResponseMapper.INSTANCE.toResponse(
                this.casualChallengeLegacyService.findAllBannedCards(seasonNumber)
        );
    }

    @GetMapping(path = PATH_EXTENDED_BANS)
    public List<BanDTO> getExtendedBans(
            @PathVariable("season") @Min(0) int seasonNumber
    ) {
        return BansResponseMapper.INSTANCE.toResponse(
                this.casualChallengeLegacyService.findAllExtendedBannedCards(seasonNumber)
        );
    }

    @GetMapping(path = PATH_CARD_PRICES)
    public Map<String, Integer> getCardPrices(
            @PathVariable("season") @Min(0) int seasonNumber
    ) {
        return CardPricesResponseMapper.INSTANCE.toResponse(
                this.casualChallengeLegacyService.findAllCardsWithBudgetPoints(seasonNumber)
        );
    }
}

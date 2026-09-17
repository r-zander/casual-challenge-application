package gg.casualchallenge.application.api;

import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.values.BanListCardVO;
import gg.casualchallenge.application.model.values.BanListVO;
import gg.casualchallenge.application.persistence.CardSeasonDataRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Season;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class BanListService {

    private final SeasonRepository seasonRepository;
    private final CardSeasonDataRepository cardSeasonDataRepository;

    public BanListService(
            SeasonRepository seasonRepository,
            CardSeasonDataRepository cardSeasonDataRepository
    ) {
        this.seasonRepository = seasonRepository;
        this.cardSeasonDataRepository = cardSeasonDataRepository;
    }

    public BanListVO getCurrentBanList() {
        Season currentSeason = this.seasonRepository.findCurrentSeason();
        List<BanListCardVO> bans = this.cardSeasonDataRepository.findAllForBanList(currentSeason, Legality.BANNED);
        List<BanListCardVO> extendedBans = this.cardSeasonDataRepository.findAllForBanList(currentSeason, Legality.EXTENDED);

        return new BanListVO(
                currentSeason.getSeasonNumber(),
                currentSeason.getStartDate(),
                currentSeason.getEndDate(),
                LocalDate.now(Constants.TIMEZONE).isAfter(currentSeason.getEndDate()),
                bans,
                extendedBans
        );
    }
}

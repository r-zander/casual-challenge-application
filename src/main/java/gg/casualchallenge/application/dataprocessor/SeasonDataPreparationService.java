package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.model.mapper.SeasonMapper;
import gg.casualchallenge.application.model.values.CreatedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonVO;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Season;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
public class SeasonDataPreparationService {

    private final SeasonRepository seasonRepository;

    public SeasonDataPreparationService(SeasonRepository seasonRepository) {
        this.seasonRepository = seasonRepository;
    }

    @Transactional
    public CreatedSeasonVO prepareSeasonData() {

//## How to: Start a new season
//
//1. Update [Season History](?tab=t.0#heading=h.442edbgnzano) by adding new season as top most row
        SeasonVO newSeason = this.createNewSeason();

//2. Update Prices
//   1. Change [janik-budgetpoints/budget\_point\_calculator.py](https://github.com/r-zander/chrome-extension-casual-challenge/blob/master/janik-budgetpoints/budget_point_calculator.py)
//      1. earliestDate \= today \- 70 days \[10 weeks \= regular season length\]
//         1. [Online tool to calculate date](https://www.timeanddate.com/date/dateadded.html?type=sub&ad=70)
//      2. latestDate \= today
//   2. Run [janik-budgetpoints/budget\_point\_calculator.py](https://github.com/r-zander/chrome-extension-casual-challenge/blob/master/janik-budgetpoints/budget_point_calculator.py) → will update data\\card-prices.json
//      1. This takes about 1 minute currently, depending on your download speed and CPU power
//   3. (Optional) Validation
//      1. Run [debug\_tools.py](https://github.com/r-zander/chrome-extension-casual-challenge/blob/master/janik-budgetpoints/debug_tools.py)
//      2. Update [2023-09-02 Price Checks](https://docs.google.com/spreadsheets/d/18YtiWDXTbMSfEcclTKDV_o6txP4zfq3NhL32Ddw3AjQ/edit#gid=0)
//      3. Verify new and changed prices

//4. Update Bans
//   1. Update Bans Spreadsheet
//      1. Go to [Casual Challenge - Bans](https://docs.google.com/spreadsheets/d/1XsEg1r56iqQCMgHgthXJI5XAeFbHjmK4j1C2T9aIPG0/edit#gid=790080717)
//      2.
//         1. This takes around 30 seconds to complete
//   2. Update JSON for extension
//      1. Copy 'Prepared Bans\!D2:D' into [data\\bans.json](https://github.com/r-zander/chrome-extension-casual-challenge/blob/master/data/bans.json)
//         1. Be careful to maintain the formatting of the file
//      2. Copy 'Prepared Extended\!D2:D' into [data\\extended-bans.json](https://github.com/r-zander/chrome-extension-casual-challenge/blob/master/data/extended-bans.json)
//         1. Be careful to maintain the formatting of the file
//      3. Due to the way MtgGoldfish handles card names, card names with diacritic characters are not transferred correctly and need to be adjusted by hand. Regular replacements are
//         1. Lorien Revealed → Lórien Revealed
//         2. Troll of Khazad-dum → Troll of Khazad-dûm
//   3. Build a local extension (to ease Step d.ii)
//      1. See [Project README, section Development](https://github.com/r-zander/chrome-extension-casual-challenge/tree/master?tab=readme-ov-file#development) for details
//   4. Create Scryfall decks for community communication
//      1. Create new Scryfall Deck: Casual Challenge \- Season $n \- New Bans
//         (e.g. `Casual Challenge - Season 15 - New Bans`)
//         1. Paste contents of 'Latest Changes\!D4:D'
//         2. Validierung Make sure all cards in the list are actually shown as banned
//      2. Create new Scryfall Deck: Casual Challenge \- Season $n \- Unbans
//         (e.g. `Casual Challenge - Season 15 - Unbans`)
//         1. Paste contents of 'Latest Changes\!A4:A'
//         2. Service Remove every card that is banned due to other formats (to avoid confusion)
//         3. Service Remove every card that costs more than 2500 BP (to avoid noise)
//      3. Replace Scryfall Deck [https://scryfall.com/@XieLong/decks/0f0409c0-4aa4-43b0-ac75-7af9a85ca3e3](https://scryfall.com/@XieLong/decks/0f0409c0-4aa4-43b0-ac75-7af9a85ca3e3)
//         1. Paste contents of 'Current\!A3:A'. Easiest version:
//            1. Copy & paste into text file
//            2. Import from file
//            3. Choose temp text file
//            4. Import and replace deck \>
//         2. Validierung Double check: Make sure all cards in the list are actually shown as banned
//         3. Service Remove every card that costs more than 2500 BP (to avoid noise)
//5. Update extensions in Chrome/Firefox store
//   1. Check README.md for release procedure
//      1. Einschränkung Only Raoul currently has access to both the Firefox and Chrome developer accounts
//   2. Commit and push changes to GitHub Repo.
//      Total Changelist should be:
//      1. U data\\bans.json
//      2. U data\\extended-bans.json
//      3. U data\\card-prices.json
//      4. U janik-budgetpoints\\budget\_point\_calculator.py
//      5. U package.json
//   3. The developer account is notified about the successful review & publishing
//      1. The review for both platforms usually takes less than an hour, sometimes mere minutes
//6. Communication
//   1. Make post in Discord
        return new CreatedSeasonVO(
            newSeason,
            null,
            null
        );
    }

    private SeasonVO createNewSeason() {
        Season currentSeason = seasonRepository.findCurrentSeason();
        int currentSeasonNumber;
        if (currentSeason == null) {
            currentSeasonNumber = 0;
        } else {
            currentSeasonNumber = currentSeason.getSeasonNumber();
            currentSeason.setEndDate(LocalDate.now(Constants.TIMEZONE).minusDays(1));
            seasonRepository.save(currentSeason);
        }

        Season newSeason = new Season();
        newSeason.setSeasonNumber(currentSeasonNumber + 1);
        newSeason.setStartDate(LocalDate.now(Constants.TIMEZONE));
        newSeason.setEndDate(findSeasonEndDate(newSeason.getStartDate()));
        seasonRepository.save(newSeason);

        return SeasonMapper.INSTANCE.toVO(newSeason);
    }

    private static LocalDate findSeasonEndDate(LocalDate startDate) {
        LocalDate tenWeeksFromNow = startDate.plusWeeks(10);
        return tenWeeksFromNow.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

}

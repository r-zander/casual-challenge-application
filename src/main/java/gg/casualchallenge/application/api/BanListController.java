package gg.casualchallenge.application.api;

import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.BanListCardVO;
import gg.casualchallenge.application.persistence.CardSeasonDataRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Season;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Hidden
@RestController
public class BanListController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final SeasonRepository seasonRepository;
    private final CardSeasonDataRepository cardSeasonDataRepository;
    private final String pageTemplate;

    private int cachedSeasonId;
    private LocalDateTime cachedUpdatedAt;
    private LocalDate cachedDay;
    private String cachedPage;

    public BanListController(
            SeasonRepository seasonRepository,
            CardSeasonDataRepository cardSeasonDataRepository
    ) throws IOException {
        this.seasonRepository = seasonRepository;
        this.cardSeasonDataRepository = cardSeasonDataRepository;
        this.pageTemplate = new ClassPathResource("bans.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(path = "/bans", produces = "text/html;charset=utf-8")
    public String getBanList() {
        Season currentSeason = this.seasonRepository.findCurrentSeason();
        LocalDate today = LocalDate.now(Constants.TIMEZONE);
        if (this.cachedPage != null
                && this.cachedSeasonId == currentSeason.getId()
                && currentSeason.getUpdatedAt().equals(this.cachedUpdatedAt)
                && today.equals(this.cachedDay)) { // the page tells whether the season has ended --> also re-render on a new day
            return this.cachedPage;
        }

        String page = renderPage(currentSeason, today);
        this.cachedPage = page;
        this.cachedSeasonId = currentSeason.getId();
        this.cachedUpdatedAt = currentSeason.getUpdatedAt();
        this.cachedDay = today;

        return page;
    }

    private String renderPage(Season currentSeason, LocalDate today) {
        List<BanListCardVO> bans = this.cardSeasonDataRepository.findAllForBanList(currentSeason, Legality.BANNED);
        List<BanListCardVO> extendedBans = this.cardSeasonDataRepository.findAllForBanList(currentSeason, Legality.EXTENDED);

        String season = "Season " + currentSeason.getSeasonNumber() + ", "
                + DATE_FORMAT.format(currentSeason.getStartDate()) + " until " + DATE_FORMAT.format(currentSeason.getEndDate()) + ".";
        String note = "";
        if (today.isAfter(currentSeason.getEndDate())) {
            note = "<p>Season " + currentSeason.getSeasonNumber() + " ended on " + DATE_FORMAT.format(currentSeason.getEndDate())
                    + " and the next one hasn't started yet, so this list still applies.</p>";
        }

        return this.pageTemplate
                .replace("{{season}}", season)
                .replace("{{note}}", note)
                .replace("{{bans}}", renderCards(bans))
                .replace("{{extended}}", renderCards(extendedBans));
    }

    private String renderCards(List<BanListCardVO> bannedCards) {
        StringBuilder entries = new StringBuilder();
        for (BanListCardVO bannedCard : bannedCards) {
            String scryfallUrl = UriComponentsBuilder.fromUriString("https://scryfall.com/search")
                    .queryParam("q", "!\"" + bannedCard.getName() + "\"")
                    .encode()
                    .toUriString();

            entries.append("<li class=\"mb-2\">");
            entries.append("<a href=\"").append(scryfallUrl).append("\">").append(HtmlUtils.htmlEscape(bannedCard.getName())).append("</a> ");
            entries.append("<span class=\"text-body-secondary\">").append(renderReasons(bannedCard)).append("</span>");
            entries.append("</li>\n");
        }

        return entries.toString();
    }

    private String renderReasons(BanListCardVO bannedCard) {
        List<String> reasons = new ArrayList<>();
        addMetaShare(reasons, MtgFormat.STANDARD, bannedCard.getMetaShareStandard());
        addMetaShare(reasons, MtgFormat.PIONEER, bannedCard.getMetaSharePioneer());
        addMetaShare(reasons, MtgFormat.PAUPER, bannedCard.getMetaSharePauper());
        addMetaShare(reasons, MtgFormat.MODERN, bannedCard.getMetaShareModern());
        addMetaShare(reasons, MtgFormat.LEGACY, bannedCard.getMetaShareLegacy());
        addMetaShare(reasons, MtgFormat.VINTAGE, bannedCard.getMetaShareVintage());
        if (bannedCard.getBannedIn() != null) reasons.add("banned in " + toDisplayName(bannedCard.getBannedIn()));
        if (bannedCard.isVintageRestricted()) reasons.add("restricted in Vintage");

        return String.join(", ", reasons);
    }

    private void addMetaShare(List<String> reasons, MtgFormat mtgFormat, BigDecimal metaShare) {
        if (metaShare == null) return;
        if (metaShare.signum() == 0) { // MtgGoldfish rounds down to full percent --> a card at the end of the list has a share of 0
            reasons.add(toDisplayName(mtgFormat) + " (< 1%)");
            return;
        }

        reasons.add(toDisplayName(mtgFormat) + " (" + metaShare.movePointRight(2).stripTrailingZeros().toPlainString() + "%)");
    }

    private String toDisplayName(MtgFormat mtgFormat) {
        return StringUtils.capitalize(mtgFormat.name().toLowerCase(Locale.ENGLISH));
    }
}

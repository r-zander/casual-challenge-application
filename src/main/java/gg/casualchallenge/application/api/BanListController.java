package gg.casualchallenge.application.api;

import gg.casualchallenge.application.model.values.BanListVO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Hidden
@Controller
public class BanListController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final BanListService banListService;

    public BanListController(BanListService banListService) {
        this.banListService = banListService;
    }

    @GetMapping(path = "/bans")
    public String getBanList(Model model) {
        BanListVO banList = this.banListService.getCurrentBanList();

        model.addAttribute("seasonNumber", banList.getSeasonNumber());
        model.addAttribute("startDate", DATE_FORMAT.format(banList.getStartDate()));
        model.addAttribute("endDate", DATE_FORMAT.format(banList.getEndDate()));
        model.addAttribute("seasonEnded", banList.isSeasonEnded());
        model.addAttribute("bans", banList.getBans());
        model.addAttribute("extendedBans", banList.getExtendedBans());

        return "bans";
    }
}

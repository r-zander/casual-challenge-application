package gg.casualchallenge.application.api;

import gg.casualchallenge.application.dataprocessor.SeasonDraftService;
import gg.casualchallenge.application.dataprocessor.SeasonRemovalService;
import gg.casualchallenge.application.dataprocessor.SeasonSanityChecks;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonRemovalPreviewVO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Hidden
@Controller
public class SeasonWizardController {

    private final SeasonDraftService seasonDraftService;
    private final SeasonRemovalService seasonRemovalService;
    private final SeasonWizardFormat seasonWizardFormat;

    public SeasonWizardController(
            SeasonDraftService seasonDraftService,
            SeasonRemovalService seasonRemovalService,
            SeasonWizardFormat seasonWizardFormat
    ) {
        this.seasonDraftService = seasonDraftService;
        this.seasonRemovalService = seasonRemovalService;
        this.seasonWizardFormat = seasonWizardFormat;
    }

    @GetMapping(path = "/season-wizard")
    public String getSeasonWizard() {
        return "season-wizard";
    }

    // The wizard reads the report as json for its own bookkeeping and picks up the rendered sections here
    @GetMapping(path = "/admin/season-wizard/draft")
    public String getDraftSections(Model model) {
        SeasonDraftReportVO report = this.seasonDraftService.report();
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season draft.");
        }

        model.addAttribute("report", report);
        model.addAttribute("sanityChecks", SeasonSanityChecks.of(report));
        model.addAttribute("format", this.seasonWizardFormat);

        return "season-wizard/draft :: sections";
    }

    @GetMapping(path = "/admin/season-wizard/season/{seasonNumber}/removal")
    public String getRemovalPreview(@PathVariable("seasonNumber") int seasonNumber, Model model) {
        SeasonRemovalPreviewVO preview = this.seasonRemovalService.preview(seasonNumber);
        if (preview == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no season " + seasonNumber + ".");
        }

        model.addAttribute("preview", preview);
        model.addAttribute("format", this.seasonWizardFormat);

        return "season-wizard/removal :: preview";
    }
}

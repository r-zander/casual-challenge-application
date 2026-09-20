package gg.casualchallenge.application.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
public class SeasonWizardController {

    @GetMapping(path = "/season-wizard")
    public String getSeasonWizard() {
        return "season-wizard";
    }
}

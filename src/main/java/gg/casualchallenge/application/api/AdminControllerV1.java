package gg.casualchallenge.application.api;

import gg.casualchallenge.application.api.datamodel.CreatedSeasonResponse;
import gg.casualchallenge.application.dataprocessor.SeasonDataPreparationService;
import gg.casualchallenge.application.model.mapper.NewSeasonMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Hidden
@RestController
@RequestMapping("/admin/v1")
public class AdminControllerV1 {

    private final SeasonDataPreparationService seasonDataPreparationService;

    public AdminControllerV1(SeasonDataPreparationService seasonDataPreparationService) {
        this.seasonDataPreparationService = seasonDataPreparationService;
    }

    @PostMapping(path = "/season/start")
    public CreatedSeasonResponse startNewSeason() {
        return NewSeasonMapper.INSTANCE.toDTO(seasonDataPreparationService.prepareSeasonData());
    }
}

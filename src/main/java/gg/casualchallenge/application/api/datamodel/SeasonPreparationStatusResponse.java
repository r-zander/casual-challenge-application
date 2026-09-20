package gg.casualchallenge.application.api.datamodel;

import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationState;
import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationStep;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class SeasonPreparationStatusResponse {
    SeasonPreparationState state;
    SeasonPreparationStep stepId;
    int stepNumber;
    String step;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    String errorMessage;
}

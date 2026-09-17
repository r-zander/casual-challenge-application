package gg.casualchallenge.application.api.datamodel;

import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationState;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class SeasonPreparationStatusResponse {
    SeasonPreparationState state;
    String step;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    String errorMessage;
}

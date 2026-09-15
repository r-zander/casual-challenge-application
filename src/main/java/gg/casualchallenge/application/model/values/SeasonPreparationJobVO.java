package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationState;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class SeasonPreparationJobVO {
    SeasonPreparationState state;
    String step;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    String errorMessage;
}

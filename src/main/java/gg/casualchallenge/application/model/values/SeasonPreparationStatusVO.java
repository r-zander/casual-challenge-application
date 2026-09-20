package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationState;
import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationStep;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class SeasonPreparationStatusVO {
    SeasonPreparationState state;
    SeasonPreparationStep stepId;
    String step;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    String errorMessage;

    public int getStepNumber() {
        return stepId != null ? stepId.getNumber() : 0;
    }
}

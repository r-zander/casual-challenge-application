package gg.casualchallenge.application.api.legacy.datamodel;

import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
public class SeasonInfoDTO {
    int seasonNumber;
    LocalDate startDate;
    LocalDate endDate;
    LocalDateTime updatedAt;
    SeasonUrlsDTO urls;
}

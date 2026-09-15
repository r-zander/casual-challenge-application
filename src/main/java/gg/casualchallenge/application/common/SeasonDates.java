package gg.casualchallenge.application.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Component
public class SeasonDates {

    private final int seasonLengthInWeeks;
    private final DayOfWeek finalsWeekday;

    public SeasonDates(
            @Value("${casual-challenge.season.length-in-weeks}") int seasonLengthInWeeks,
            @Value("${casual-challenge.season.finals-weekday}") DayOfWeek finalsWeekday
    ) {
        this.seasonLengthInWeeks = seasonLengthInWeeks;
        this.finalsWeekday = finalsWeekday;
    }

    public LocalDate defaultEndDate(LocalDate previousSeasonEnd) {
        return previousSeasonEnd.plusWeeks(seasonLengthInWeeks);
    }

    public LocalDate finalsFriday(LocalDate endDate) {
        return endDate.with(TemporalAdjusters.previousOrSame(finalsWeekday));
    }

    public LocalDate nextSeasonStart(LocalDate endDate) {
        return endDate.plusDays(1);
    }
}

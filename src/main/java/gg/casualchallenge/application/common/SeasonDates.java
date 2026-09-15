package gg.casualchallenge.application.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class SeasonDates {

    private static final int REGULAR_SEASON_LENGTH_IN_WEEKS = 10;

    private SeasonDates() {}

    public static LocalDate defaultEndDate(LocalDate previousSeasonEnd) {
        return previousSeasonEnd.plusWeeks(REGULAR_SEASON_LENGTH_IN_WEEKS);
    }

    public static LocalDate finalsFriday(LocalDate endDate) {
        return endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    }

    public static LocalDate nextSeasonStart(LocalDate endDate) {
        return endDate.plusDays(1);
    }
}

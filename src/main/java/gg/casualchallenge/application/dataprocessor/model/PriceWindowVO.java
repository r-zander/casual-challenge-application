package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Value
public class PriceWindowVO {
    LocalDate start;
    LocalDate end;

    public static PriceWindowVO of(LocalDate seasonStart, int days) {
        return new PriceWindowVO(seasonStart.minusDays(days), seasonStart);
    }

    public int length() {
        return (int) ChronoUnit.DAYS.between(start, end);
    }

    public int dayIndex(LocalDate date) {
        if (date.isBefore(start) || !date.isBefore(end)) return -1;
        return (int) ChronoUnit.DAYS.between(start, date);
    }

}

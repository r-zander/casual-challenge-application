package gg.casualchallenge.application.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonDatesTest {

    @Test
    void testDefaultEndDate() {
        assertEquals(LocalDate.of(2026, 11, 22), SeasonDates.defaultEndDate(LocalDate.of(2026, 9, 13)));
        assertEquals(LocalDate.of(2026, 6, 14), SeasonDates.defaultEndDate(LocalDate.of(2026, 4, 5)));
        assertEquals(LocalDate.of(2025, 3, 1), SeasonDates.defaultEndDate(LocalDate.of(2024, 12, 21)));
    }

    @Test
    void testFinalsFriday() {
        assertEquals(LocalDate.of(2026, 11, 20), SeasonDates.finalsFriday(LocalDate.of(2026, 11, 22)));
        assertEquals(LocalDate.of(2026, 11, 20), SeasonDates.finalsFriday(LocalDate.of(2026, 11, 21)));
        assertEquals(LocalDate.of(2026, 11, 20), SeasonDates.finalsFriday(LocalDate.of(2026, 11, 20)));
        assertEquals(LocalDate.of(2026, 11, 20), SeasonDates.finalsFriday(LocalDate.of(2026, 11, 26)));
        assertEquals(LocalDate.of(2026, 9, 11), SeasonDates.finalsFriday(LocalDate.of(2026, 9, 13)));
    }

    @Test
    void testNextSeasonStart() {
        assertEquals(LocalDate.of(2026, 11, 23), SeasonDates.nextSeasonStart(LocalDate.of(2026, 11, 22)));
        assertEquals(LocalDate.of(2027, 1, 1), SeasonDates.nextSeasonStart(LocalDate.of(2026, 12, 31)));
    }
}

package gg.casualchallenge.application.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RomanNumeralTest {

    @Test
    void testOf() {
        assertEquals("I", RomanNumeral.of(1));
        assertEquals("IV", RomanNumeral.of(4));
        assertEquals("IX", RomanNumeral.of(9));
        assertEquals("XVIII", RomanNumeral.of(18));
        assertEquals("XIX", RomanNumeral.of(19));
        assertEquals("XX", RomanNumeral.of(20));
        assertEquals("XXI", RomanNumeral.of(21));
        assertEquals("XL", RomanNumeral.of(40));
        assertEquals("XCIX", RomanNumeral.of(99));
        assertEquals("CDXLIV", RomanNumeral.of(444));
        assertEquals("MCMXCIV", RomanNumeral.of(1994));
        assertEquals("MMMCMXCIX", RomanNumeral.of(3999));
    }

    @Test
    void testOf_withUnsupportedNumbers() {
        assertThrows(IllegalArgumentException.class, () -> RomanNumeral.of(0));
        assertThrows(IllegalArgumentException.class, () -> RomanNumeral.of(-1));
        assertThrows(IllegalArgumentException.class, () -> RomanNumeral.of(4000));
    }
}

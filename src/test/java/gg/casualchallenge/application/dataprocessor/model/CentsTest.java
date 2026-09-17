package gg.casualchallenge.application.dataprocessor.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CentsTest {

    @Test
    void testFromPrice() {
        assertEquals(Cents.of(0), Cents.fromPrice(new BigDecimal("0")));
        assertEquals(Cents.of(1), Cents.fromPrice(new BigDecimal("0.01")));
        assertEquals(Cents.of(125), Cents.fromPrice(new BigDecimal("1.25")));
        assertEquals(Cents.of(100), Cents.fromPrice(new BigDecimal("1.0")));
        assertEquals(Cents.of(600000), Cents.fromPrice(new BigDecimal("6000.00")));
    }

    @Test
    void testAverage() {
        assertEquals(Cents.of(0), Cents.of(500).average(0));
        assertEquals(Cents.of(0), Cents.of(0).average(70));
        assertEquals(Cents.of(121), Cents.of(12149).average(100));
        assertEquals(Cents.of(12), Cents.of(25).average(2));
        assertEquals(Cents.of(14), Cents.of(27).average(2));
        assertEquals(Cents.of(24), Cents.of(49).average(2));
        assertEquals(Cents.of(26), Cents.of(51).average(2));
        assertEquals(Cents.of(320), Cents.of(22400).average(70));
    }

    @Test
    void testDividedBy() {
        assertEquals(Cents.of(100), Cents.of(110).dividedBy(1.1));
        assertEquals(Cents.of(0), Cents.of(0).dividedBy(1.1));
        assertEquals(Cents.of(12), Cents.of(25).dividedBy(2.0));
        assertEquals(Cents.of(14), Cents.of(27).dividedBy(2.0));
        assertEquals(Cents.of(2273), Cents.of(2500).dividedBy(1.1));
    }

}

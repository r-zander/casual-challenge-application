package gg.casualchallenge.application.dataprocessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetPointsTest {

    @Test
    void testFromAverage() {
        assertEquals(0, BudgetPoints.fromAverage(0));
        assertEquals(121, BudgetPoints.fromAverage(1.2149));
        assertEquals(12, BudgetPoints.fromAverage(0.125));
        assertEquals(14, BudgetPoints.fromAverage(0.135));
        assertEquals(34, BudgetPoints.fromAverage(0.345));
        assertEquals(100, BudgetPoints.fromAverage(1.005));
    }

    @Test
    void testAdjustExchangeRate() {
        assertEquals(1.0, BudgetPoints.adjustExchangeRate(1.0));
        assertEquals(1.05, BudgetPoints.adjustExchangeRate(1.1));
        assertEquals(0.5, BudgetPoints.adjustExchangeRate(0));
    }

    @Test
    void testFromUsd() {
        assertEquals(100, BudgetPoints.fromUsd(110, 1.1));
        assertEquals(0, BudgetPoints.fromUsd(0, 1.1));
        assertEquals(12, BudgetPoints.fromUsd(25, 2.0));
        assertEquals(2273, BudgetPoints.fromUsd(2500, 1.1));
    }

}

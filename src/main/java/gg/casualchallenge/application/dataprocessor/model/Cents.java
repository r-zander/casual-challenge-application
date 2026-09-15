package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Value
public class Cents { // a budget point is one cent, so card prices and budget points are the same number

    long amount;

    public static Cents of(long amount) {
        return new Cents(amount);
    }

    public static Cents fromPrice(BigDecimal price) {
        return new Cents(price.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact());
    }

    public Cents plus(Cents other) {
        return new Cents(amount + other.amount);
    }

    public boolean isLessThan(Cents other) {
        return amount < other.amount;
    }

    // Half to even is what the round() of the python tool this comes from does, and one exact division beats summing doubles
    public Cents average(int days) {
        if (days == 0) return of(0);

        return new Cents(BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_EVEN).longValueExact());
    }

    public Cents dividedBy(double rate) {
        return new Cents(BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(rate), 0, RoundingMode.HALF_EVEN).longValueExact());
    }

    public int toBudgetPoints() {
        return Math.toIntExact(amount);
    }
}

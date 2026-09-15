package gg.casualchallenge.application.dataprocessor;

public final class BudgetPoints {

    // rint rounds half to even - same as the round() of the python tool this comes from
    public static int fromAverage(double eurAverage) {
        return (int) Math.rint(eurAverage * 100);
    }

    public static double adjustExchangeRate(double averageRate) {
        return (1 + averageRate) / 2; // to be defensive, the average exchange rate is reduced
    }

    public static int fromUsd(int usdCents, double adjustedRate) {
        return (int) Math.rint(usdCents / adjustedRate);
    }

    private BudgetPoints() {}
}

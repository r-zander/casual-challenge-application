package gg.casualchallenge.application.common;

public final class RomanNumeral {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private RomanNumeral() {}

    public static String of(int number) {
        if (number < 1 || number > 3999) {
            throw new IllegalArgumentException("Can't write '" + number + "' as a roman numeral, only 1 to 3999 are supported.");
        }

        StringBuilder romanNumeral = new StringBuilder();
        int remainder = number;
        for (int index = 0; index < VALUES.length; index++) {
            while (remainder >= VALUES[index]) {
                romanNumeral.append(SYMBOLS[index]);
                remainder -= VALUES[index];
            }
        }

        return romanNumeral.toString();
    }
}

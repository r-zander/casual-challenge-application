package gg.casualchallenge.library.api;

import java.util.Locale;

public final class ParameterUtil {
    private ParameterUtil() {}

    /**
     * Takes the value of a request parameter and returns true if the parameter is just there
     * OR somewhat evaluates to "true".
     */
    public static boolean getPresenceBoolean(String parameter) {
        if (parameter == null) {
            return false;
        }

        if (parameter.isEmpty()) {
            return true;
        }

        String lowerCaseParam = parameter.toLowerCase(Locale.ROOT);
        switch (lowerCaseParam) {
            case "true":
            case "1":
            case "yes":
            case "y":
            case "on":
                return true;
            default:
                return false;
        }
    }
}

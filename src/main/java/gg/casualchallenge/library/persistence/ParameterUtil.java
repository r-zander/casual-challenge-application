package gg.casualchallenge.library.persistence;

import java.util.Locale;

public final class ParameterUtil {
    private ParameterUtil() {}

    /**
     * Takes the value of a request parameter and returns true if the parameter is just there OR somewhat evaluates to "true".
     */
    public static boolean getPresenceBoolean(String parameter) {
        if (parameter == null) {
            return false;
        }

        if (parameter.isEmpty()) {
            return true;
        }

        String lowerCaseParam = parameter.toLowerCase(Locale.ROOT);
        return lowerCaseParam.equals("true") ||
                lowerCaseParam.equals("1") ||
                lowerCaseParam.equals("yes") ||
                lowerCaseParam.equals("y") ||
                lowerCaseParam.equals("on");
    }
}

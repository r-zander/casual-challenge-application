package gg.casualchallenge.application.model.type;

import gg.casualchallenge.library.persistence.DbEnumValue;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum MtgFormat {
    @DbEnumValue("standard")
    STANDARD,
    @DbEnumValue("pioneer")
    PIONEER,
    @DbEnumValue("pauper")
    PAUPER,
    @DbEnumValue("modern")
    MODERN,
    @DbEnumValue("legacy")
    LEGACY,
    @DbEnumValue("vintage")
    VINTAGE,
    ;

    public String getDisplayName() {
        return StringUtils.capitalize(name().toLowerCase(Locale.ENGLISH));
    }
}

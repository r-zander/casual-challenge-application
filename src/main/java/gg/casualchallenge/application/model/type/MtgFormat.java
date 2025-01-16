package gg.casualchallenge.application.model.type;

import gg.casualchallenge.library.persistence.DbEnumValue;

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
}

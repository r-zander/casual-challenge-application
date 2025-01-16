package gg.casualchallenge.application.model.type;

import gg.casualchallenge.library.persistence.DbEnumValue;

public enum Legality {
    @DbEnumValue("legal")
    LEGAL,
    @DbEnumValue("not_legal")
    NOT_LEGAL,
    @DbEnumValue("extended")
    EXTENDED,
    @DbEnumValue("banned")
    BANNED
}

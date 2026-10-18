package gg.casualchallenge.application.dataprocessor.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import gg.casualchallenge.library.persistence.DbEnumValue;

/** The uppercase aliases are for the older archived request.json files, those hold the constants themselves */
public enum MetaShareSource {
    @JsonProperty("mtggoldfish")
    @JsonAlias("MTGGOLDFISH")
    @DbEnumValue("mtggoldfish")
    MTGGOLDFISH,
    @JsonProperty("mtgtop8")
    @JsonAlias("MTGTOP8")
    @DbEnumValue("mtgtop8")
    MTGTOP8,
    @JsonProperty("files")
    @JsonAlias("FILES")
    @DbEnumValue("files")
    FILES,
    ;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}

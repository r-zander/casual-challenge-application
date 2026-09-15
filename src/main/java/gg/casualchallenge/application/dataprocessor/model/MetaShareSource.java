package gg.casualchallenge.application.dataprocessor.model;

public enum MetaShareSource {
    MTGGOLDFISH,
    MTGTOP8,
    FILES;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}

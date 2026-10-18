package gg.casualchallenge.application.dataprocessor.model;

public enum SeasonPreparationStep {
    /** The warm-up line, none of the five has begun. */
    STARTING(0),
    READING_ALL_PRINTINGS(1),
    READING_ALL_PRICES(2),
    READING_META_SHARES(3),
    ASSEMBLING_THE_DRAFT(4),
    STORING_THE_DRAFT(5),
    /** The closing line, the five are through. */
    READY_FOR_REVIEW(5),
    ;

    private final int number;

    SeasonPreparationStep(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }
}

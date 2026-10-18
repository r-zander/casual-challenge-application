package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Does the draft add up? The wizard shows these above the report and locks the commit on a blocking one. */
@Value
public class SeasonSanityChecksVO {
    List<SanityCheckVO> checks;

    public boolean isCommitBlocked() {
        return !getBlockingLabels().isEmpty();
    }

    public List<String> getBlockingLabels() {
        List<String> labels = new ArrayList<>();
        for (SanityCheckVO check : this.checks) {
            if (!check.isBlocking()) continue;
            if (check.isPassing()) continue;

            labels.add(check.getLabel().toLowerCase(Locale.ENGLISH));
        }

        return labels;
    }

    @Value
    public static class SanityCheckVO {
        String label;
        String actual;
        String expectation;
        boolean passing;
        /** A failing one of these means the numbers are wrong, not just surprising. */
        boolean blocking;
    }
}

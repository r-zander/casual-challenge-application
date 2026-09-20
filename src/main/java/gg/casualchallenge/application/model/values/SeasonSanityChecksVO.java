package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Value
public class SeasonSanityChecksVO { // does the draft add up? the wizard shows these above the report and locks the commit on a blocking one
    List<SanityCheckVO> checks;

    public boolean isCommitBlocked() {
        return !getBlockingLabels().isEmpty();
    }

    public List<String> getBlockingLabels() {
        List<String> labels = new ArrayList<>();
        for (SanityCheckVO check : this.checks) {
            if (check.isBlocking() && !check.isPassing()) labels.add(check.getLabel().toLowerCase(Locale.ENGLISH));
        }

        return labels;
    }

    @Value
    public static class SanityCheckVO {
        String label;
        String actual;
        String expectation;
        boolean passing;
        boolean blocking; // a failing one of these means the numbers are wrong, not just surprising
    }
}

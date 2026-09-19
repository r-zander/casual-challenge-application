package gg.casualchallenge.application.model.values;

import lombok.Value;

@Value
public class PullRequestVO {
    int number;
    String url;
    String branch;
    String commitSha;
}

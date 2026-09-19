package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

@Value
public class PullRequestDTO {
    int number;
    String url;
    String branch;
    String commitSha;
}

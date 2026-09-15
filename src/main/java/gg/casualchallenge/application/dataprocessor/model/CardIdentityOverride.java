package gg.casualchallenge.application.dataprocessor.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CardIdentityOverride {
    private List<String> excludedSets;
    private String oracleId;
}

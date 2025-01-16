package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.util.UUID;

@Value
public class CardVO {
    int id;
    UUID oracleId;
    String name;
    String normalizedName;
}

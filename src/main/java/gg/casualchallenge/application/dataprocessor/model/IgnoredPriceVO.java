package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

@Value
public class IgnoredPriceVO {
    String set;
    String number; // null = every printing of the set
    String finish; // null = every finish of the printing
}

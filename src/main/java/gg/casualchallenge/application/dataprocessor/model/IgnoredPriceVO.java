package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.type.Finish;
import lombok.Value;

@Value
public class IgnoredPriceVO {
    String set;
    /** null = every printing of the set */
    String number;
    /** null = every finish of the printing */
    Finish finish;
}

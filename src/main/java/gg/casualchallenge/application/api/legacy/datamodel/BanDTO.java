package gg.casualchallenge.application.api.legacy.datamodel;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
public class BanDTO {
    String name;
    Map<LegacyMtgFormat, BigDecimal> formats;
}

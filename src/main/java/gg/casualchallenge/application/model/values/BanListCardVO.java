package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class BanListCardVO {
    String name;
    BigDecimal metaShareStandard;
    BigDecimal metaSharePioneer;
    BigDecimal metaSharePauper;
    BigDecimal metaShareModern;
    BigDecimal metaShareLegacy;
    BigDecimal metaShareVintage;
    MtgFormat bannedIn;
    boolean vintageRestricted;
}

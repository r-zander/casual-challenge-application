package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
public class SeasonDraftCardVO {
    UUID oracleId;
    UUID previousOracleId;
    String name;
    String normalizedName;
    Integer budgetPoints;
    Legality legality;
    BigDecimal metaShareStandard;
    BigDecimal metaSharePioneer;
    BigDecimal metaShareModern;
    BigDecimal metaShareLegacy;
    BigDecimal metaShareVintage;
    BigDecimal metaSharePauper;
    MtgFormat bannedIn;
    boolean vintageRestricted;
    boolean isNewCard;
    String skipReason;
}

package gg.casualchallenge.application.api.datamodel;

import gg.casualchallenge.application.model.type.AppliedRule;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class CardDTO {
    String name;
    String normalizedName;
    UUID oracleId;

    Integer budgetPoints;
    Legality legality;
    ReasonDTO reason;

    @Value
    public static class ReasonDTO {
        Set<AppliedRule> appliedRules;
        Map<MtgFormat, BigDecimal> metaShares;
        Set<MtgFormat> bannedIn;
    }
}

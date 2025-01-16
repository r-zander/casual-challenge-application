package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.AppliedRule;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class CardWithDataVO {
    int id;
    UUID oracleId;
    String name;
    String normalizedName;

    Integer budgetPoints;
    Legality legality;
    ReasonVO reason;

    @Value
    public static class ReasonVO {
        Set<AppliedRule> appliedRules;
        Map<MtgFormat, BigDecimal> metaShares;
        Set<MtgFormat> bannedIn;
    }

}

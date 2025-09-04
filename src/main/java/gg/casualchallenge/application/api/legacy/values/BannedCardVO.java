package gg.casualchallenge.application.api.legacy.values;

import gg.casualchallenge.application.api.legacy.datamodel.LegacyMtgFormat;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Value
public class BannedCardVO {
    UUID cardOracleId;
    String cardName;
    Map<LegacyMtgFormat, BigDecimal> metaShares;
}

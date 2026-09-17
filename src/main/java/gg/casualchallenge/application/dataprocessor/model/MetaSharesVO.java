package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.datamodel.LegacyMtgFormat;
import gg.casualchallenge.application.common.CardNameNormalizer;
import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
public class MetaSharesVO {

    Map<String, Map<MtgFormat, BigDecimal>> bans;
    Map<String, Map<MtgFormat, BigDecimal>> extendedBans;
    Map<MtgFormat, Integer> top50Rows; // rows per format, sanity check in the report
    Map<MtgFormat, Integer> top150Rows;
    List<String> duplicateNames;
    MetaShareSource source;

    public Map<MtgFormat, BigDecimal> findBan(String cardName) {
        return findMetaShares(bans, cardName);
    }

    public Map<MtgFormat, BigDecimal> findExtendedBan(String cardName) {
        return findMetaShares(extendedBans, cardName);
    }

    private static Map<MtgFormat, BigDecimal> findMetaShares(Map<String, Map<MtgFormat, BigDecimal>> metaSharesByName, String cardName) {
        Map<MtgFormat, BigDecimal> metaShares = metaSharesByName.get(CardNameNormalizer.normalize(cardName));
        if (metaShares != null) return metaShares;

        // MtgGoldfish only ever knows the front face of a double faced card --> try that one, too
        int separator = cardName.indexOf("//");
        if (separator < 0) return null;

        return metaSharesByName.get(CardNameNormalizer.normalize(cardName.substring(0, separator)));
    }

    public static MetaSharesVO fromStaples(MetaShareSource source, Map<MtgFormat, List<Staple>> top50, Map<MtgFormat, List<Staple>> top150) {
        Set<String> duplicateNames = new LinkedHashSet<>();
        return new MetaSharesVO(
                toMetaSharesByName(top50, duplicateNames),
                toMetaSharesByName(top150, duplicateNames),
                countStaplesPerFormat(top50),
                countStaplesPerFormat(top150),
                new ArrayList<>(duplicateNames),
                source);
    }

    public static MetaSharesVO fromBanFiles(List<BanDTO> bans, List<BanDTO> extendedBans) {
        Set<String> duplicateNames = new LinkedHashSet<>();
        return new MetaSharesVO(
                toMetaSharesByName(bans, duplicateNames),
                toMetaSharesByName(extendedBans, duplicateNames),
                countCardsPerFormat(bans),
                countCardsPerFormat(extendedBans),
                new ArrayList<>(duplicateNames),
                MetaShareSource.FILES);
    }

    private static Map<String, Map<MtgFormat, BigDecimal>> toMetaSharesByName(Map<MtgFormat, List<Staple>> staplesByFormat, Set<String> duplicateNames) {
        Map<String, Map<MtgFormat, BigDecimal>> metaSharesByName = new HashMap<>();
        for (Map.Entry<MtgFormat, List<Staple>> entry : staplesByFormat.entrySet()) {
            for (Staple staple : entry.getValue()) {
                BigDecimal knownShare = metaSharesByName
                        .computeIfAbsent(CardNameNormalizer.normalize(staple.getCardName()), cardName -> new EnumMap<>(MtgFormat.class))
                        .put(entry.getKey(), staple.getPercentageOfDecks());
                if (knownShare != null) duplicateNames.add(staple.getCardName());
            }
        }

        return metaSharesByName;
    }

    private static Map<String, Map<MtgFormat, BigDecimal>> toMetaSharesByName(List<BanDTO> bans, Set<String> duplicateNames) {
        Map<String, Map<MtgFormat, BigDecimal>> metaSharesByName = new HashMap<>();
        for (BanDTO ban : bans) {
            Map<MtgFormat, BigDecimal> metaShares = new EnumMap<>(MtgFormat.class);
            for (Map.Entry<LegacyMtgFormat, BigDecimal> entry : ban.getFormats().entrySet()) {
                metaShares.put(toMtgFormat(entry.getKey()), entry.getValue());
            }
            // A second row for the same card replaces the first one, just like the python tool does - the report says which ones
            if (metaSharesByName.put(CardNameNormalizer.normalize(ban.getName()), metaShares) != null) duplicateNames.add(ban.getName());
        }

        return metaSharesByName;
    }

    private static Map<MtgFormat, Integer> countStaplesPerFormat(Map<MtgFormat, List<Staple>> staplesByFormat) {
        Map<MtgFormat, Integer> rowCounts = new EnumMap<>(MtgFormat.class);
        for (Map.Entry<MtgFormat, List<Staple>> entry : staplesByFormat.entrySet()) {
            rowCounts.put(entry.getKey(), entry.getValue().size());
        }

        return rowCounts;
    }

    private static Map<MtgFormat, Integer> countCardsPerFormat(List<BanDTO> bans) {
        Map<MtgFormat, Integer> rowCounts = new EnumMap<>(MtgFormat.class);
        for (BanDTO ban : bans) {
            for (LegacyMtgFormat legacyMtgFormat : ban.getFormats().keySet()) {
                rowCounts.merge(toMtgFormat(legacyMtgFormat), 1, Integer::sum);
            }
        }

        return rowCounts;
    }

    private static MtgFormat toMtgFormat(LegacyMtgFormat legacyMtgFormat) {
        return MtgFormat.valueOf(legacyMtgFormat.name()); // both enums spell their constants the same way
    }
}

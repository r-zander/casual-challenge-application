package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class MtgJsonPrintingsVO {
    Map<String, MtgJsonCard> cardsByName;
    Map<UUID, MtgJsonPrinting> printingsByUuid;
    Map<String, List<MtgJsonPrinting>> ignoredPrintingsByCardName; // IgnoredPrices.json, foil and nonFoil are the finishes that never get a price
    List<MtgJsonSet> sets;
    LocalDate metaDate;
    String metaVersion;

    public Map<String, String> getSetNamesByCode() {
        Map<String, String> setNamesByCode = new HashMap<>(sets.size());
        for (MtgJsonSet mtgSet : sets) {
            setNamesByCode.put(mtgSet.getCode(), mtgSet.getName());
        }

        return setNamesByCode;
    }
}

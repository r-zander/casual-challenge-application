package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Value
public class MtgJsonPrintingsVO {
    Map<String, MtgJsonCard> cardsByName;
    Map<String, MtgJsonPrinting> printingsByUuid;
    List<MtgJsonSet> sets;
    LocalDate metaDate;
    String metaVersion;
}

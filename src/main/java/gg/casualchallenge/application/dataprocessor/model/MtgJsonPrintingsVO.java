package gg.casualchallenge.application.dataprocessor.model;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class MtgJsonPrintingsVO {
    Map<String, MtgJsonCard> cardsByName;
    Map<UUID, MtgJsonPrinting> printingsByUuid;
    List<MtgJsonSet> sets;
    LocalDate metaDate;
    String metaVersion;
}

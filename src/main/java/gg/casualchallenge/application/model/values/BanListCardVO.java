package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.MtgFormat;
import lombok.Value;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    public String getScryfallUrl() {
        return UriComponentsBuilder.fromUriString("https://scryfall.com/search")
                .queryParam("q", "!\"" + this.name + "\"")
                .encode()
                .toUriString();
    }

    public List<String> getReasons() {
        List<String> reasons = new ArrayList<>();
        addMetaShare(reasons, MtgFormat.STANDARD, this.metaShareStandard);
        addMetaShare(reasons, MtgFormat.PIONEER, this.metaSharePioneer);
        addMetaShare(reasons, MtgFormat.PAUPER, this.metaSharePauper);
        addMetaShare(reasons, MtgFormat.MODERN, this.metaShareModern);
        addMetaShare(reasons, MtgFormat.LEGACY, this.metaShareLegacy);
        addMetaShare(reasons, MtgFormat.VINTAGE, this.metaShareVintage);
        if (this.bannedIn != null) reasons.add("banned in " + this.bannedIn.getDisplayName());
        if (this.vintageRestricted) reasons.add("restricted in Vintage");

        return reasons;
    }

    private void addMetaShare(List<String> reasons, MtgFormat mtgFormat, BigDecimal metaShare) {
        if (metaShare == null) return;
        if (metaShare.signum() == 0) { // MtgGoldfish rounds down to full percent --> a card at the end of the list has a share of 0
            reasons.add(mtgFormat.getDisplayName() + " (< 1%)");
            return;
        }

        reasons.add(mtgFormat.getDisplayName() + " (" + metaShare.movePointRight(2).stripTrailingZeros().toPlainString() + "%)");
    }
}

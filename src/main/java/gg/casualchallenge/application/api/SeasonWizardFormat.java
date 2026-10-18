package gg.casualchallenge.application.api;

import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.MtgSetVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The season wizard fragments run through here, so the browser's locale never gets a say. */
@Component
public class SeasonWizardFormat {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DOC_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH);

    public String date(LocalDate date) {
        if (date == null) return "";

        return DATE_FORMAT.format(date);
    }

    public String dateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";

        return DATE_FORMAT.format(dateTime) + ", " + TIME_FORMAT.format(dateTime);
    }

    /** The Season History table writes them German style. */
    public String docDate(LocalDate date) {
        if (date == null) return "";

        return DOC_DATE_FORMAT.format(date);
    }

    public String number(Integer value) {
        if (value == null) return "";

        return String.format(Locale.ENGLISH, "%,d", value);
    }

    public String budgetPoints(Integer budgetPoints) {
        if (budgetPoints == null) return "-";

        return number(budgetPoints);
    }

    public String change(int change) {
        if (change > 0) return "+" + number(change);

        return number(change);
    }

    /** Same reasons as the ban list page, in the same order */
    public String banReasons(SeasonDraftReportVO.BanChangeVO card) {
        List<String> reasons = new ArrayList<>();
        Map<MtgFormat, BigDecimal> metaShares = card.getMetaShares();
        for (MtgFormat mtgFormat : MtgFormat.values()) {
            BigDecimal metaShare = metaShares != null ? metaShares.get(mtgFormat) : null;
            if (metaShare == null) continue;

            // MtgGoldfish rounds down to full percent --> a card at the end of the list has a share of 0
            if (metaShare.signum() == 0) {
                reasons.add(mtgFormat.getDisplayName() + " (< 1%)");
                continue;
            }

            String percentage = metaShare.movePointRight(2).stripTrailingZeros().toPlainString();
            reasons.add(mtgFormat.getDisplayName() + " (" + percentage + "%)");
        }
        if (card.getBannedIn() != null) reasons.add("banned in " + card.getBannedIn().getDisplayName());
        if (card.isVintageRestricted()) reasons.add("restricted in Vintage");

        return String.join(", ", reasons);
    }

    public String scryfallUrl(String name) {
        return UriComponentsBuilder.fromUriString("https://scryfall.com/search")
                .queryParam("q", "!\"" + name + "\"")
                .encode()
                .toUriString();
    }

    /** Drafts from before the admin name was tracked have neither prepared_by nor committed_by. */
    public String byLine(String name) {
        if (name == null) return "";

        return " by " + name;
    }

    public String cardCount(String deckList) {
        if (deckList == null) return "0 cards";
        if (deckList.trim().isEmpty()) return "0 cards";

        // TODO "1 cards"
        return deckList.trim().split("\n").length + " cards";
    }

    public String joined(List<String> values) {
        if (values == null) return "";

        return String.join(", ", values);
    }

    public String childCodes(MtgSetVO mtgSet) {
        if (mtgSet.getChildSets() == null) return "";

        List<String> codes = new ArrayList<>(mtgSet.getChildSets().size());
        for (MtgSetVO.ChildSetVO childSet : mtgSet.getChildSets()) {
            codes.add(childSet.getCode());
        }

        return String.join(", ", codes);
    }

    public List<String> setNames(List<MtgSetVO> sets) {
        // A report an older build wrote has no sets in it at all
        if (sets == null) return List.of();

        List<String> names = new ArrayList<>(sets.size());
        for (MtgSetVO mtgSet : sets) {
            String commanderDecksSuffix = "";
            if (mtgSet.getCommanderDecks() != null) {
                if (!mtgSet.getCommanderDecks().isEmpty()) commanderDecksSuffix = " (+ Commander Decks)";
            }
            names.add(mtgSet.getName() + commanderDecksSuffix);
        }

        return names;
    }

    public List<String> setCodes(List<MtgSetVO> sets) {
        if (sets == null) return List.of();

        List<String> codes = new ArrayList<>();
        for (MtgSetVO mtgSet : sets) {
            codes.add(mtgSet.getCode());
            if (mtgSet.getChildSets() == null) continue;

            for (MtgSetVO.ChildSetVO childSet : mtgSet.getChildSets()) {
                codes.add(childSet.getCode());
            }
        }

        return codes;
    }
}

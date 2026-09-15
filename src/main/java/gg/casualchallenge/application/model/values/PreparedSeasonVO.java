package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.util.List;

@Value
public class PreparedSeasonVO {
    List<SeasonDraftCardVO> cards;
    SeasonDraftReportVO report;
}

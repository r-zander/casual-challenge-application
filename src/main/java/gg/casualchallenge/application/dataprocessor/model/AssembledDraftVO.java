package gg.casualchallenge.application.dataprocessor.model;

import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import lombok.Value;

import java.util.List;

@Value
public class AssembledDraftVO {
    List<SeasonDraftCardVO> cards;
    SeasonDraftReportVO report;
}

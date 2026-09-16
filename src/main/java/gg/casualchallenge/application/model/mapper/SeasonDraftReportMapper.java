package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.SeasonDraftReportResponse;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SeasonDraftReportMapper {
    SeasonDraftReportMapper INSTANCE = Mappers.getMapper(SeasonDraftReportMapper.class);

    SeasonDraftReportResponse toResponse(SeasonDraftReportVO seasonDraftReportVO);
}

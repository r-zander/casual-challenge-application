package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CardSeasonDataRepository  extends ListCrudRepository<CardSeasonData, Long>  {
    List<CardSeasonData> findAllBySeasonAndCardOracleIdIn(Season season, Collection<UUID> cardOracleIds);
}

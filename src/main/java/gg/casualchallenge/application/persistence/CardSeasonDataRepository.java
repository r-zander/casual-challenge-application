package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CardSeasonDataRepository  extends ListCrudRepository<CardSeasonData, Long>  {
    List<CardSeasonData> findAllBySeasonAndCardOracleIdIn(Season season, Collection<UUID> cardOracleIds);

    List<CardSeasonData> findAllBySeason(Season season);

    @Query(value = "SELECT csd FROM CardSeasonData csd WHERE csd.season = :season AND csd.legality = :legality" +
            " AND (csd.metaShareStandard IS NOT NULL" +
            " OR csd.metaSharePioneer IS NOT NULL" +
            " OR csd.metaShareModern IS NOT NULL" +
            " OR csd.metaShareLegacy IS NOT NULL" +
            " OR csd.metaShareVintage IS NOT NULL" +
            " OR csd.metaSharePauper IS NOT NULL)" /*+
            " AND csd.bannedIn IS NULL" +
            " AND csd.vintageRestricted = FALSE"*/)
    List<CardSeasonData> findAllByMetaBan(Season season, Legality legality);
}

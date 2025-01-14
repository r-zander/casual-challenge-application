package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.persistence.entity.Season;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface SeasonRepository extends ListCrudRepository<Season, Integer> {

    /**
     * The current season is always the season with the latest start date.
     */
    @Query("SELECT s FROM Season s ORDER BY s.startDate DESC LIMIT 1")
    Season findCurrentSeason();

}

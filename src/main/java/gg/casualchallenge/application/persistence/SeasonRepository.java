package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.persistence.entity.Season;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface SeasonRepository extends ListCrudRepository<Season, Integer> {

    /**
     * The current season is always the season with the latest start date.
     * TODO this assumes seasons are not scheduled into the future (which is the case), but still important to know!
     *      Fix would mean to potentially act on "no active season", which has no defined behavior yet.
     */
    @Query("SELECT s FROM Season s ORDER BY s.startDate DESC LIMIT 1")
    Season findCurrentSeason();

    Season findBySeasonNumber(int seasonNumber);
}

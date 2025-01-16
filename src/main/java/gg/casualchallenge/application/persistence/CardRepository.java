package gg.casualchallenge.application.persistence;

import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.Season;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface CardRepository extends ListCrudRepository<Card, Integer> {
}

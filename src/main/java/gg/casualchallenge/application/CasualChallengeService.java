package gg.casualchallenge.application;

import gg.casualchallenge.application.model.values.CardVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CasualChallengeService {

    public List<CardVO> findCards(List<String> names) {
        return names.stream().map(CardVO::new).toList();
    }

}

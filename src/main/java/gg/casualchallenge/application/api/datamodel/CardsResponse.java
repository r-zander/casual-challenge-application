package gg.casualchallenge.application.api.datamodel;

import lombok.Value;

import java.util.List;

/*
 Example:
    <code>
    {
      "found": [
        {
          "name": "Goblin Blast-Runner",
          "normalizedName": "goblin-blast-runner",
          "oracleId": "8b69da01-3ba5-4a61-9e26-e879b6d8b63e",
          "budgetPoints": 26,
          "legality": "EXTENDED",
          "reason": {
            "appliedRules": [
              "TOURNAMENT_STAPLE"
            ],
            "metaShares": {
              "PAUPER": 0.05
            },
            "bannedIn": []
          }
        },
        {
          "name": "Ashiok, Dream Render",
          "normalizedName": "ashiok-dream-render",
          "oracleId": "93723b12-db34-4047-885e-8606415b1553",
          "budgetPoints": 114,
          "legality": "LEGAL",
          "reason": {
            "appliedRules": [
              "NO_BANS",
              "VINTAGE_LEGAL"
            ],
            "metaShares": {},
            "bannedIn": []
          }
        }
      ],

      "missing": [
        "Wonky Man"
      ]
    }
    </code>
 */

@Value
public class CardsResponse {
    List<CardDTO> found;
    List<String> missing;
}

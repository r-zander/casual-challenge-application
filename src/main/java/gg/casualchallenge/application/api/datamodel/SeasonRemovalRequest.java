package gg.casualchallenge.application.api.datamodel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter // no @Value and no @Data: the token has no business showing up in a toString
public class SeasonRemovalRequest {

    @Schema(description = "A GitHub token with write access to contents and pull requests. Needed when the commit opened a pull request: the branch goes away with the season. It is used for this one call and stored nowhere.", example = "github_pat_...")
    private String githubToken;
}

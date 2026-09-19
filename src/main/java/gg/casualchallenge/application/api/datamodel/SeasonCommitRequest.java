package gg.casualchallenge.application.api.datamodel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter // no @Value and no @Data: the token has no business showing up in a toString
public class SeasonCommitRequest {

    @Schema(description = "A GitHub token with write access to contents and pull requests. Leave it out and the commit works as it always did, with the three migrations as downloads. It is used for this one call and stored nowhere.", example = "github_pat_...")
    private String githubToken;
}

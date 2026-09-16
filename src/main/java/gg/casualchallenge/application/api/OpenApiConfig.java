package gg.casualchallenge.application.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_TOKEN = "bearerToken";

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/admin/**")
                .build();
    }

    @Bean
    public OpenAPI casualChallengeApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Casual Challenge API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_TOKEN)) // every group needs a token, so it sits on the whole document
                .components(new Components()
                        .addSecuritySchemes(BEARER_TOKEN, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

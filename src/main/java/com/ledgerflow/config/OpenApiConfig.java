package com.ledgerflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI ledgerFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerFlow API")
                        .version("v1")
                        .description(
                                """
                                Double-entry bookkeeping API. Every transaction is a set of entries \
                                whose debits and credits sum to zero; that invariant is enforced \
                                before anything is written.

                                Authenticate via POST /auth/login and send the returned token as \
                                `Authorization: Bearer <token>`. List endpoints are paged and accept \
                                `page`, `size` and `sort`, and return a `PagedResponse` envelope. \
                                Errors carry a stable `code` alongside the human-readable `message`.\
                                """))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

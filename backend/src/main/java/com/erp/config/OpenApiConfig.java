package com.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for Swagger UI (served at /swagger-ui.html).
 * The bearer security scheme adds an "Authorize" button so JWT-protected
 * endpoints can be executed directly from the browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI erpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ERP System API")
                        .description(
                                "REST API for the ERP System (Inventory, Purchasing, Sales). "
                                        + "Login via /api/auth/login, copy the accessToken, click Authorize "
                                        + "and paste it to call protected endpoints.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

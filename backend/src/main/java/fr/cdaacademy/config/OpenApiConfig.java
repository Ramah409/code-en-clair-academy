package fr.cdaacademy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Documentation Swagger accessible sur /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI cdaAcademyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CDA Academy API")
                        .version("1.0.0")
                        .description("API de la plateforme d'apprentissage CDA Academy. "
                                + "Se connecter via POST /api/auth/login puis cliquer sur « Authorize » avec le jeton reçu."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

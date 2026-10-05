package org.kodewerks.pollsystem.dev

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Local-only OpenAPI metadata for Swagger UI (/swagger-ui.html). Declares the
 * app's `Authorization: Bearer <JWT>` scheme so the UI shows an Authorize button
 * and sends the token with every "Try it out" request. Public endpoints simply
 * ignore it. Get a token from POST /api/dev/token or /api/dev/seed-user.
 */
@Configuration
@Profile("local")
class OpenApiConfig {

    @Bean
    fun pollSystemOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Poll System API (local)")
                .description(
                    "Local dev only. Authorize with a JWT from POST /api/dev/token?email=… " +
                        "(e.g. admin@local.test) or from POST /api/dev/seed-user."
                )
        )
        .components(
            Components().addSecuritySchemes(
                BEARER,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
            )
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER))

    private companion object {
        const val BEARER = "bearerAuth"
    }
}

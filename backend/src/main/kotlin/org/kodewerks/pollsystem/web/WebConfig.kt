package org.kodewerks.pollsystem.web

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registers the poll kill-switch interceptor over the poll SUBMISSION endpoints
 * only — POST /api/polls/{type}/{id}/responses. Reads (viewing, search, results,
 * "my responses") stay up while polls are disabled, and admin/super management
 * endpoints are untouched, so the switch can always be turned back off.
 */
@Configuration
class WebConfig(private val pollsDisabledInterceptor: PollsDisabledInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(pollsDisabledInterceptor).addPathPatterns("/api/polls/*/*/responses")
    }
}

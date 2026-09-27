package org.kodewerks.pollsystem.web

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registers the poll kill-switch interceptor over the public poll API. Scoped
 * to the /api/polls path so admin and super endpoints — including the toggle
 * that clears the flag — keep working while polls are disabled.
 */
@Configuration
class WebConfig(private val pollsDisabledInterceptor: PollsDisabledInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(pollsDisabledInterceptor).addPathPatterns("/api/polls/**")
    }
}

package org.kodewerks.pollsystem.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.kodewerks.pollsystem.superadmin.GlobalFlagService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Global kill-switch enforcement. When a Super-admin has set the
 * `polls_disabled` flag, poll SUBMISSIONS (write requests to the responses
 * endpoints this interceptor is mapped to) short-circuit with 503 before any
 * controller/DB work runs — shedding write load in an emergency. Reads
 * (viewing, search, results, "my responses") are left untouched, as are
 * admin/super management endpoints, so the switch can still be turned back off.
 *
 * This is unrelated to the per-poll admin block feature (poll_type_blocks).
 */
@Component
class PollsDisabledInterceptor(private val flags: GlobalFlagService) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // Only block mutating requests; reads (GET incl. /me, and OPTIONS preflight) pass.
        if (request.method !in WRITE_METHODS) return true
        if (flags.pollsDisabled()) {
            response.status = HttpStatus.SERVICE_UNAVAILABLE.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"message":"Poll submissions are temporarily disabled by an administrator"}""")
            return false
        }
        return true
    }

    companion object {
        private val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}

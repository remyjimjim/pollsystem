package org.kodewerks.pollsystem.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.kodewerks.pollsystem.superadmin.GlobalFlagService
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Global kill-switch enforcement. When a Super-admin has set the
 * `polls_disabled` flag, every public poll endpoint (under /api/polls) short-
 * circuits with 503 before any controller/DB work runs — shedding load in an
 * emergency. Admin/Super management endpoints live under other namespaces and
 * are unaffected, so the switch can still be turned back off.
 *
 * This is unrelated to the per-poll admin block feature (poll_type_blocks).
 */
@Component
class PollsDisabledInterceptor(private val flags: GlobalFlagService) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // Let CORS preflight through untouched.
        if (request.method == HttpMethod.OPTIONS.name()) return true
        if (flags.pollsDisabled()) {
            response.status = HttpStatus.SERVICE_UNAVAILABLE.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"message":"Polls are temporarily disabled by an administrator"}""")
            return false
        }
        return true
    }
}

package org.kodewerks.pollsystem.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * A request that fails validation says which fields, in plain words ("Please
 * check your phone number."), instead of Spring's "Validation failed for
 * object='guestCheckoutRequest'. Error count: 1", which the pages show as is.
 * Same shape as Spring's own error responses (timestamp, status, error,
 * message, path).
 */
@RestControllerAdvice
class ValidationErrors {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        // The validator reports problems in no fixed order, so sort them: the
        // form's own order first (email, phone, zipcode), then the rest A-Z.
        val fields = e.bindingResult.fieldErrors.map { it.field }.distinct()
            .sortedWith(compareBy<String>({ ORDER.indexOf(it).let { i -> if (i < 0) ORDER.size else i } }, { it }))
            .map { LABELS[it] ?: it }
        val message = if (fields.isEmpty()) "Please check your details and try again."
            else "Please check your ${join(fields)}."
        return ResponseEntity.badRequest().body(mapOf(
            "timestamp" to Instant.now().toString(),
            "status" to HttpStatus.BAD_REQUEST.value(),
            "error" to HttpStatus.BAD_REQUEST.reasonPhrase,
            "message" to message,
            "path" to request.requestURI
        ))
    }

    private fun join(parts: List<String>) = when (parts.size) {
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    companion object {
        private val ORDER = listOf("email", "phone", "zipcode")
        private val LABELS = mapOf(
            "email" to "email address",
            "phone" to "phone number",
            "zipcode" to "zipcode (5 digits)"
        )
    }
}

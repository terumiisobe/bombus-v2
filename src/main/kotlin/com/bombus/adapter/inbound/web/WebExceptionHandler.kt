package com.bombus.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Maps web-adapter exceptions to RFC 7807 problem responses without leaking internals. */
@RestControllerAdvice
class WebExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidTwilioSignatureException::class)
    fun handleInvalidSignature(ex: InvalidTwilioSignatureException, request: HttpServletRequest): ProblemDetail {
        log.warn("Rejected webhook request to {}: {}", request.requestURI, ex.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Request signature verification failed").apply {
            title = "Forbidden"
        }
    }
}

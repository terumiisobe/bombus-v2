package com.bombus.adapter.inbound.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Maps web-adapter exceptions to RFC 7807 problem responses without leaking internals. */
@RestControllerAdvice
class WebExceptionHandler {

    @ExceptionHandler(InvalidTwilioSignatureException::class)
    fun handleInvalidSignature(ex: InvalidTwilioSignatureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Request signature verification failed").apply {
            title = "Forbidden"
        }
}

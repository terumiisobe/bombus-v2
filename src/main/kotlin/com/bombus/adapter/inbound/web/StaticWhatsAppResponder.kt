package com.bombus.adapter.inbound.web

import org.springframework.stereotype.Component

/**
 * Skeleton stub for the D2 walking skeleton: returns a fixed pt-BR reply regardless of
 * the incoming message. Replaced by the real inbound use case (customer resolution,
 * intent parsing, counting) in later deliverables (D3/D6).
 */
@Component
class StaticWhatsAppResponder {

    fun reply(): String = "Olá! Em breve poderei contar suas colmeias."
}

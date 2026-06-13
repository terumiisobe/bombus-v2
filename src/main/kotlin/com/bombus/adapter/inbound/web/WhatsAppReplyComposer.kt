package com.bombus.adapter.inbound.web

import com.bombus.chatbot.domain.CustomerResolution
import org.springframework.stereotype.Component

/**
 * Composes the interim pt-BR reply for a resolved/unknown sender. This is scaffolding for the
 * identity deliverable; later flows replace it with the understand -> count -> reply pipeline.
 */
@Component
class WhatsAppReplyComposer {

    fun compose(resolution: CustomerResolution): String = when (resolution) {
        is CustomerResolution.Resolved -> {
            val name = resolution.customer.displayName?.takeIf { it.isNotBlank() }
            if (name != null) {
                "Olá, $name! Identifiquei sua conta. Em breve poderei contar suas colmeias."
            } else {
                "Olá! Identifiquei sua conta. Em breve poderei contar suas colmeias."
            }
        }

        CustomerResolution.NotLinked ->
            "Não encontrei uma conta vinculada a este número. Por favor, entre em contato com o suporte."
    }
}

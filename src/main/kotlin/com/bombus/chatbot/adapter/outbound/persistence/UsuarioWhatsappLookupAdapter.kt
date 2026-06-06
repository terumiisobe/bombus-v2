package com.bombus.chatbot.adapter.outbound.persistence

import com.bombus.chatbot.application.port.outbound.WhatsAppUserLookupPort
import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.PhoneNumber
import org.springframework.stereotype.Component

/**
 * Driven adapter implementing [WhatsAppUserLookupPort] over the usuario_whatsapp table.
 * Only active links are considered; the entity is mapped to the domain [Customer] here.
 */
@Component
class UsuarioWhatsappLookupAdapter(
    private val repository: UsuarioWhatsappJpaRepository,
) : WhatsAppUserLookupPort {

    override fun findActiveByPhone(phone: PhoneNumber): Customer? =
        repository.findByPhoneNumberAndActiveTrue(phone.value)?.toCustomer()

    private fun UsuarioWhatsappEntity.toCustomer(): Customer = Customer(
        whatsappUserId = requireNotNull(id) { "A persisted usuario_whatsapp row must have an id" },
        userId = usuarioId,
        displayName = displayName,
    )
}

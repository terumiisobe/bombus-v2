package com.bombus.chatbot.application.port.outbound

import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.PhoneNumber

/**
 * Driven (outbound) port: looks up the active WhatsApp link for a phone number.
 *
 * Returns the mapped [Customer], or `null` when there is no active link for the number.
 * Read-only by contract — implementations must not mutate the link.
 */
interface WhatsAppUserLookupPort {

    fun findActiveByPhone(phone: PhoneNumber): Customer?
}

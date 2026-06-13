package com.bombus.chatbot.application

import com.bombus.chatbot.application.port.inbound.ResolveCustomerUseCase
import com.bombus.chatbot.application.port.outbound.WhatsAppUserLookupPort
import com.bombus.chatbot.domain.CustomerResolution
import com.bombus.chatbot.domain.PhoneNumber
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves an inbound phone number to a customer. Validation of the number shape lives in
 * the [PhoneNumber] value object; a missing/inactive link surfaces as [CustomerResolution.NotLinked].
 */
@Service
@Transactional(readOnly = true)
class ResolveCustomerService(
    private val lookupPort: WhatsAppUserLookupPort,
) : ResolveCustomerUseCase {

    override fun resolve(phoneNumber: String): CustomerResolution {
        val phone = PhoneNumber(phoneNumber)
        return when (val customer = lookupPort.findActiveByPhone(phone)) {
            null -> CustomerResolution.NotLinked
            else -> CustomerResolution.Resolved(customer)
        }
    }
}

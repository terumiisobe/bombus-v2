package com.bombus.chatbot.application.port.inbound

import com.bombus.chatbot.domain.CustomerResolution

/**
 * Driving (inbound) port: resolve a raw phone number (already stripped of any channel
 * prefix by the adapter) to a [CustomerResolution].
 */
interface ResolveCustomerUseCase {

    fun resolve(phoneNumber: String): CustomerResolution
}

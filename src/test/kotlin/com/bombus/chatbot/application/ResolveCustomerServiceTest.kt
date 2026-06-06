package com.bombus.chatbot.application

import com.bombus.chatbot.application.port.outbound.WhatsAppUserLookupPort
import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.CustomerResolution
import com.bombus.chatbot.domain.PhoneNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ResolveCustomerServiceTest {

    @Test
    fun `resolves a known active phone to the mapped customer`() {
        val customer = Customer(whatsappUserId = 7, userId = 42, displayName = "Ana")
        val service = ResolveCustomerService(FakeLookupPort(mapOf("+5511999999999" to customer)))

        val result = service.resolve("+5511999999999")

        val resolved = assertIs<CustomerResolution.Resolved>(result)
        assertEquals(customer, resolved.customer)
    }

    @Test
    fun `returns NotLinked when no active link exists for the phone`() {
        val service = ResolveCustomerService(FakeLookupPort(emptyMap()))

        val result = service.resolve("+5511999999999")

        assertSame(CustomerResolution.NotLinked, result)
    }

    @Test
    fun `rejects a malformed phone before touching the lookup port`() {
        val port = FakeLookupPort(emptyMap())
        val service = ResolveCustomerService(port)

        assertFailsWith<IllegalArgumentException> { service.resolve("not-a-number") }
        assertEquals(0, port.calls)
    }

    @Test
    fun `rejects a blank phone before touching the lookup port`() {
        val port = FakeLookupPort(emptyMap())
        val service = ResolveCustomerService(port)

        assertFailsWith<IllegalArgumentException> { service.resolve("") }
        assertEquals(0, port.calls)
    }

    private class FakeLookupPort(
        private val byPhone: Map<String, Customer>,
    ) : WhatsAppUserLookupPort {

        var calls: Int = 0
            private set

        override fun findActiveByPhone(phone: PhoneNumber): Customer? {
            calls++
            return byPhone[phone.value]
        }
    }
}

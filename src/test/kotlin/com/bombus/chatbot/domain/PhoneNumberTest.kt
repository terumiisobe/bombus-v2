package com.bombus.chatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhoneNumberTest {

    @Test
    fun `accepts a canonical E164 number and preserves the value`() {
        val phone = PhoneNumber("+5511999999999")

        assertEquals("+5511999999999", phone.value)
    }

    @Test
    fun `rejects a number without the leading plus`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber("5511999999999") }
    }

    @Test
    fun `rejects a value still carrying the whatsapp channel prefix`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber("whatsapp:+5511999999999") }
    }

    @Test
    fun `rejects a blank value`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber("   ") }
    }

    @Test
    fun `rejects a value with non-digit characters`() {
        assertFailsWith<IllegalArgumentException> { PhoneNumber("+55 11 99999-9999") }
    }
}

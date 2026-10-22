package com.bombus.colmeia.application

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColmeiaCountPropertiesTest {

    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `disposition statuses are excluded at mark for counts`() {
        assertTrue(properties.isDispositionStatus("perdida"))
        assertTrue(properties.isDispositionStatus("vendida"))
        assertTrue(properties.releasesCode("perdida"))
        assertFalse(properties.isDispositionStatus("estavel"))
        assertFalse(properties.isDispositionStatus(null))
    }
}

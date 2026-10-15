package com.bombus.colmeia.adapter.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeliponarioMapStatusMapperTest {

    @Test
    fun `maps known domain labels to english api strings`() {
        assertEquals("developing", MeliponarioMapStatusMapper.toApiStatus("desenvolvendo"))
        assertEquals("recovering", MeliponarioMapStatusMapper.toApiStatus("recuperando"))
        assertEquals("stable", MeliponarioMapStatusMapper.toApiStatus("estavel"))
        assertEquals("lost", MeliponarioMapStatusMapper.toApiStatus("perdida"))
        assertEquals("unknown", MeliponarioMapStatusMapper.toApiStatus("desconhecido"))
        assertEquals("sold", MeliponarioMapStatusMapper.toApiStatus("vendida"))
    }

    @Test
    fun `null stays null and unknown names pass through`() {
        assertNull(MeliponarioMapStatusMapper.toApiStatus(null))
        assertEquals("novo_status", MeliponarioMapStatusMapper.toApiStatus("novo_status"))
    }
}

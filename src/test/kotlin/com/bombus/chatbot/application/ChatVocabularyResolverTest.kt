package com.bombus.chatbot.application

import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatVocabularyResolverTest {

    private val species = listOf(
        SpeciesRef(1, "JT", "Jataí", "Tetragonisca angustula"),
        SpeciesRef(9, "SD", "Canudo", "Scaptotrigona depilis"),
        SpeciesRef(6, "MD", "Manduri", "Melipona marginata"),
        SpeciesRef(7, "MT", "Manduri", "Melipona torrida"),
    )

    private val statuses = listOf(
        StatusRef(1, "em_desenvolvimento"),
        StatusRef(3, "estavel"),
    )

    @Test
    fun `resolves species by common name ignoring accent and case`() {
        val result = ChatVocabularyResolver.resolveSpecies("JATAI", species)
        assertIs<ChatVocabularyResolver.SpeciesResolution.Found>(result)
        assertEquals(1L, result.species.id)
    }

    @Test
    fun `resolves species by abbreviation`() {
        val result = ChatVocabularyResolver.resolveSpecies("sd", species)
        assertIs<ChatVocabularyResolver.SpeciesResolution.Found>(result)
        assertEquals(9L, result.species.id)
    }

    @Test
    fun `resolves species by scientific name`() {
        val result = ChatVocabularyResolver.resolveSpecies("Scaptotrigona depilis", species)
        assertIs<ChatVocabularyResolver.SpeciesResolution.Found>(result)
        assertEquals(9L, result.species.id)
    }

    @Test
    fun `reports ambiguous common name`() {
        val result = ChatVocabularyResolver.resolveSpecies("manduri", species)
        assertIs<ChatVocabularyResolver.SpeciesResolution.Ambiguous>(result)
        assertEquals(2, result.matches.size)
    }

    @Test
    fun `resolves status equating underscore and spaces`() {
        val result = ChatVocabularyResolver.resolveStatus("Em Desenvolvimento", statuses)
        assertIs<ChatVocabularyResolver.StatusResolution.Found>(result)
        assertEquals(1L, result.status.id)
    }

    @Test
    fun `unknown status lists valid names`() {
        val result = ChatVocabularyResolver.resolveStatus("voando", statuses)
        assertIs<ChatVocabularyResolver.StatusResolution.Unknown>(result)
        assertTrue(result.validNames.contains("estavel"))
    }
}

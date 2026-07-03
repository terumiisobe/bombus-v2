package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import kotlin.test.Test
import kotlin.test.assertEquals

class ListColmeiaVocabularyServiceTest {

    @Test
    fun `returns the species and statuses provided by the port unchanged`() {
        val species = listOf(
            SpeciesRef(id = 1, abbreviation = "JT", commonName = "Jataí", scientificName = "Tetragosnisca angustula"),
            SpeciesRef(id = 2, abbreviation = "EM", commonName = "Mirim emerina", scientificName = "Plebeia emerina"),
        )
        val statuses = listOf(
            StatusRef(id = 1, name = "desenvolvendo"),
            StatusRef(id = 4, name = "perdida"),
        )
        val service = ListColmeiaVocabularyService(FakeVocabularyPort(species, statuses))

        val result = service.list()

        assertEquals(species, result.species)
        assertEquals(statuses, result.statuses)
    }

    @Test
    fun `returns empty vocabulary when the port has none`() {
        val service = ListColmeiaVocabularyService(FakeVocabularyPort(emptyList(), emptyList()))

        val result = service.list()

        assertEquals(emptyList(), result.species)
        assertEquals(emptyList(), result.statuses)
    }

    private class FakeVocabularyPort(
        private val species: List<SpeciesRef>,
        private val statuses: List<StatusRef>,
    ) : ColmeiaVocabularyPort {
        override fun listSpecies(): List<SpeciesRef> = species
        override fun listStatuses(): List<StatusRef> = statuses
    }
}

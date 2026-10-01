package com.bombus.chatbot.application

import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import java.text.Normalizer

/**
 * Resolves user/model vocabulary labels to internal ids at the chat-tool boundary.
 * Matching is case- and accent-insensitive; underscores and spaces are equivalent.
 */
internal object ChatVocabularyResolver {

    sealed class SpeciesResolution {
        data class Found(val species: SpeciesRef) : SpeciesResolution()
        data class Unknown(val input: String, val validCommonNames: List<String>) : SpeciesResolution()
        data class Ambiguous(val input: String, val matches: List<SpeciesRef>) : SpeciesResolution()
    }

    sealed class StatusResolution {
        data class Found(val status: StatusRef) : StatusResolution()
        data class Unknown(val input: String, val validNames: List<String>) : StatusResolution()
    }

    fun resolveSpecies(raw: String, species: List<SpeciesRef>): SpeciesResolution {
        val key = normalize(raw)
        if (key.isEmpty()) {
            return SpeciesResolution.Unknown(raw, species.map { it.commonName }.distinct().sorted())
        }

        val byCommon = species.filter { normalize(it.commonName) == key }
        when {
            byCommon.size == 1 -> return SpeciesResolution.Found(byCommon.single())
            byCommon.size > 1 -> return SpeciesResolution.Ambiguous(raw, byCommon)
        }

        val byAbbreviation = species.filter { normalize(it.abbreviation) == key }
        if (byAbbreviation.size == 1) return SpeciesResolution.Found(byAbbreviation.single())
        if (byAbbreviation.size > 1) return SpeciesResolution.Ambiguous(raw, byAbbreviation)

        val byScientific = species.filter { normalize(it.scientificName) == key }
        if (byScientific.size == 1) return SpeciesResolution.Found(byScientific.single())
        if (byScientific.size > 1) return SpeciesResolution.Ambiguous(raw, byScientific)

        return SpeciesResolution.Unknown(raw, species.map { it.commonName }.distinct().sorted())
    }

    fun resolveStatus(raw: String, statuses: List<StatusRef>): StatusResolution {
        val key = normalize(raw)
        if (key.isEmpty()) {
            return StatusResolution.Unknown(raw, statuses.map { it.name }.sorted())
        }
        val matches = statuses.filter { normalize(it.name) == key }
        return when {
            matches.size == 1 -> StatusResolution.Found(matches.single())
            else -> StatusResolution.Unknown(raw, statuses.map { it.name }.sorted())
        }
    }

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(DIACRITICS, "")
            .replace('_', ' ')
            .replace(WHITESPACE, " ")
            .trim()
    }

    private val DIACRITICS = "\\p{M}+".toRegex()
    private val WHITESPACE = "\\s+".toRegex()
}

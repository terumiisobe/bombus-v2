package com.bombus.chatbot.domain

// Chatbot-local view of the countable vocabulary. Kept separate from the colmeia context's
// own refs so the two bounded contexts stay decoupled; the orchestrator maps between them.
data class CountVocabulary(
    val species: List<SpeciesTerm>,
    val statuses: List<StatusTerm>,
)

data class SpeciesTerm(
    val id: Long,
    val abbreviation: String,
    val commonName: String,
    val scientificName: String,
)

data class StatusTerm(
    val id: Long,
    val name: String,
)

package com.bombus.colmeia.domain

/**
 * Expected write/list failures surfaced to chat tools as structured JSON.
 * Illegal combinations are rejected at the use-case boundary, not deeper.
 */
sealed class ColmeiaCommandError(message: String) : Exception(message) {
    class NoMeliponario : ColmeiaCommandError("Customer has no meliponário")
    class ColmeiaNotFound : ColmeiaCommandError("Colmeia not found for this customer")
    class AmbiguousCode : ColmeiaCommandError("Code matches multiple colmeias")
    class CodeTaken : ColmeiaCommandError("Code already in use in this meliponário")
    class UnknownSpecies : ColmeiaCommandError("Unknown speciesId")
    class UnknownStatus : ColmeiaCommandError("Unknown statusId")
    class InvalidLimit : ColmeiaCommandError("limit must be between 1 and 50")
    class ConfirmationRequired : ColmeiaCommandError("Delete requires confirmed=true after user confirmation")
}

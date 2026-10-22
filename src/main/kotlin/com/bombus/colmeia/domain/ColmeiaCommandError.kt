package com.bombus.colmeia.domain

/**
 * Expected write/list failures surfaced to chat tools as structured JSON.
 * Illegal combinations are rejected at the use-case boundary, not deeper.
 */
sealed class ColmeiaCommandError(message: String) : Exception(message) {
    class NoMeliponario : ColmeiaCommandError("Customer has no meliponário")
    class ColmeiaNotFound : ColmeiaCommandError("Colmeia not found for this customer")
    /** Yard missing or caller is not a meliponario_membro — surfaced as HTTP 404. */
    class MeliponarioNotFound : ColmeiaCommandError("Meliponário not found for this customer")
    class AmbiguousCode : ColmeiaCommandError("Code matches multiple colmeias")
    class AmbiguousDisposition(statusNames: List<String>) : ColmeiaCommandError(
        "Code matches colmeias with statuses ${statusNames.joinToString()}. No hive was changed or deleted.",
    )
    class CodeTaken : ColmeiaCommandError("Code already in use in this meliponário")
    class UnknownSpecies : ColmeiaCommandError("Unknown speciesId")
    class UnknownStatus : ColmeiaCommandError("Unknown statusId")
    class InvalidLimit : ColmeiaCommandError("limit must be between 1 and 50")
    class ConfirmationRequired : ColmeiaCommandError("Delete requires confirmed=true after user confirmation")
    /** Visit 2 applies only to perdida/vendida. */
    class NotDispositionStatus : ColmeiaCommandError(
        "Code liberation applies only to colmeias perdida or vendida",
    )
    /** Visit 2 N/A: never-pinned at mark or already cleared — code already free. */
    class NoLivePinToClear : ColmeiaCommandError(
        "Colmeia has no live pin; code is already free to reuse",
    )
}

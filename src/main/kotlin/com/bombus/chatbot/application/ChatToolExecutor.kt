package com.bombus.chatbot.application

import com.bombus.chatbot.domain.AssistantToolCall
import com.bombus.chatbot.domain.ToolDefinition
import com.bombus.chatbot.domain.ToolResultMessage
import com.bombus.colmeia.application.ColmeiaCountProperties
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.CreateColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.DetailColmeiaQuery
import com.bombus.colmeia.application.port.inbound.DetailColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryQuery
import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusCommand
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusUseCase
import com.bombus.colmeia.application.port.inbound.ResolveColmeiaIdUseCase
import com.bombus.colmeia.application.port.inbound.ResolveColmeiaQuery
import com.bombus.colmeia.application.port.inbound.ResolvedColmeia
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.ColmeiaVocabulary
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Builds tool definitions and executes tool calls against colmeia use cases.
 * Counts and hive mutations come only from use cases; the model phrases replies.
 * Species/status are named at the tool boundary; ids stay internal to use cases.
 */
@Component
class ChatToolExecutor(
    private val countColmeias: CountColmeiasUseCase,
    private val vocabularyUseCase: ListColmeiaVocabularyUseCase,
    private val resolveColmeiaId: ResolveColmeiaIdUseCase,
    private val detailColmeia: DetailColmeiaUseCase,
    private val listColmeiaHistory: ListColmeiaHistoryUseCase,
    private val createColmeia: CreateColmeiaUseCase,
    private val recordColmeiaStatus: RecordColmeiaStatusUseCase,
    private val deleteColmeia: DeleteColmeiaUseCase,
    private val objectMapper: ObjectMapper,
    private val countProperties: ColmeiaCountProperties,
    private val visitFormatter: FieldVisitReplyFormatter = FieldVisitReplyFormatter(),
) {

    fun definitions(): List<ToolDefinition> = listOf(
        COUNT_COLMEIAS_DEF,
        LIST_VOCABULARY_DEF,
        DETAIL_COLMEIA_DEF,
        LIST_COLMEIA_HISTORY_DEF,
        CREATE_COLMEIA_DEF,
        UPDATE_COLMEIA_DEF,
        DELETE_COLMEIA_DEF,
    )

    fun execute(userId: Long, call: AssistantToolCall): ToolResultMessage {
        val contentJson = try {
            when (call.name) {
                ChatToolNames.COUNT_COLMEIAS -> executeCount(userId, call.argumentsJson)
                ChatToolNames.LIST_VOCABULARY -> executeListVocabulary()
                ChatToolNames.DETAIL_COLMEIA -> executeDetail(userId, call.argumentsJson)
                ChatToolNames.LIST_COLMEIA_HISTORY -> executeListHistory(userId, call.argumentsJson)
                ChatToolNames.CREATE_COLMEIA -> executeCreate(userId, call.argumentsJson)
                ChatToolNames.UPDATE_COLMEIA -> executeUpdate(userId, call.argumentsJson)
                ChatToolNames.DELETE_COLMEIA -> executeDelete(userId, call.argumentsJson)
                else -> errorJson("unknown_tool", "Unknown tool: ${call.name}")
            }
        } catch (ex: ColmeiaCommandError) {
            errorJson(ex::class.simpleName ?: "command_error", ex.message ?: "command failed")
        } catch (ex: Exception) {
            errorJson("tool_error", ex.message ?: "tool execution failed")
        }
        return ToolResultMessage(toolCallId = call.id, name = call.name, contentJson = contentJson)
    }

    private fun executeCount(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val vocabulary = vocabularyUseCase.list()
        val speciesOutcome = resolveOptionalSpecies(args.optionalString("species"), vocabulary)
        if (speciesOutcome is ResolveOutcome.Failed) return speciesOutcome.errorJson
        val statusOutcome = resolveOptionalStatus(args.optionalString("status"), vocabulary)
        if (statusOutcome is ResolveOutcome.Failed) return statusOutcome.errorJson
        val speciesRef = (speciesOutcome as ResolveOutcome.Ok).value
        val statusRef = (statusOutcome as ResolveOutcome.Ok).value

        val groupBy = args.path("groupBy")
            .takeIf { it.isArray }
            ?.mapNotNull { node ->
                when (node.asText().uppercase()) {
                    "SPECIES" -> CountDimension.SPECIES
                    "STATUS" -> CountDimension.STATUS
                    else -> null
                }
            }
            ?.toSet()
            .orEmpty()

        val count = countColmeias.count(
            CountColmeiasQuery(
                userId = userId,
                speciesId = speciesRef?.id,
                statusId = statusRef?.id,
                groupBy = groupBy,
            ),
        )

        val payload = linkedMapOf<String, Any?>(
            "total" to count.total,
            "species" to speciesRef?.commonName,
            "speciesScientificName" to speciesRef?.scientificName,
            "speciesAbbreviation" to speciesRef?.abbreviation,
            "status" to statusRef?.name,
            "excludedStatusLabel" to if (statusRef == null) countProperties.excludedStatusLabel() else null,
            "groupBy" to groupBy.map { it.name },
        )
        count.perSpecies?.let { breakdown ->
            payload["perSpecies"] = breakdown.map {
                mapOf(
                    "commonName" to it.commonName,
                    "abbreviation" to it.abbreviation,
                    "count" to it.count,
                )
            }
        }
        count.perStatus?.let { breakdown ->
            payload["perStatus"] = breakdown.map {
                mapOf(
                    "status" to it.statusName,
                    "count" to it.count,
                )
            }
        }
        return objectMapper.writeValueAsString(payload)
    }

    private fun executeListVocabulary(): String {
        val vocabulary = vocabularyUseCase.list()
        val payload = mapOf(
            "species" to vocabulary.species.map {
                mapOf(
                    "commonName" to it.commonName,
                    "abbreviation" to it.abbreviation,
                    "scientificName" to it.scientificName,
                )
            },
            "statuses" to vocabulary.statuses.map {
                mapOf("name" to it.name)
            },
        )
        return objectMapper.writeValueAsString(payload)
    }

    private fun executeDetail(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val code = args.optionalInt("code") ?: return errorJson("missing_code", "code is required")
        val resolved = resolveHive(userId, code, args.optionalString("status"))
        if (resolved is ResolveHive.Failed) return resolved.errorJson
        val hive = (resolved as ResolveHive.Ok).value
        val detail = detailColmeia.detail(DetailColmeiaQuery(userId = userId, colmeiaId = hive.colmeiaId))
        val scientificName = scientificNameForCommonName(detail.speciesCommonName)
        val lastVisit = detail.lastVisit?.let { entry ->
            mapOf(
                "date" to visitFormatter.formatDate(entry),
                "status" to entry.statusName,
                "note" to entry.note,
            )
        }
        return objectMapper.writeValueAsString(
            mapOf(
                "code" to detail.code,
                "species" to detail.speciesCommonName,
                "speciesScientificName" to scientificName,
                "status" to detail.statusName,
                "lastVisit" to lastVisit,
            ),
        )
    }

    private fun executeListHistory(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val code = args.optionalInt("code")
            ?: return errorJson("missing_code", "code is required")
        val resolved = resolveHive(userId, code, args.optionalString("status"))
        if (resolved is ResolveHive.Failed) return resolved.errorJson
        val entriesNewestFirst = listColmeiaHistory.list(
            ListColmeiaHistoryQuery(
                userId = userId,
                colmeiaId = (resolved as ResolveHive.Ok).value.colmeiaId,
                limit = args.optionalInt("limit") ?: ListColmeiaHistoryQuery.DEFAULT_LIMIT,
            ),
        )
        // Last N from the use case (newest first); present chronologically ascending for WhatsApp.
        val entries = entriesNewestFirst.asReversed()
        val lines = if (entries.isEmpty()) {
            listOf(visitFormatter.emptyHistory(code))
        } else {
            entries.map { visitFormatter.format(it) }
        }
        return objectMapper.writeValueAsString(
            mapOf(
                "code" to code,
                "count" to entries.size,
                "lines" to lines,
                "visits" to entries.map { entry ->
                    mapOf(
                        "date" to visitFormatter.formatDate(entry),
                        "status" to entry.statusName,
                        "note" to entry.note,
                    )
                },
            ),
        )
    }

    private fun executeCreate(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val speciesRaw = args.optionalString("species")
            ?: return errorJson("missing_species", "species (common name) is required")
        val vocabulary = vocabularyUseCase.list()
        val species = when (val resolved = ChatVocabularyResolver.resolveSpecies(speciesRaw, vocabulary.species)) {
            is ChatVocabularyResolver.SpeciesResolution.Found -> resolved.species
            is ChatVocabularyResolver.SpeciesResolution.Unknown ->
                return errorJson(
                    "unknown_species",
                    "Unknown species '$speciesRaw'. Valid common names: ${resolved.validCommonNames.joinToString()}",
                )
            is ChatVocabularyResolver.SpeciesResolution.Ambiguous ->
                return ambiguousSpeciesJson(resolved.input, resolved.matches)
        }
        val code = args.optionalInt("code")
        if (code == null && !args.path("confirmed").asBoolean(false)) {
            return errorJson(
                "confirmation_required",
                "Código não informado. Confirme para cadastrar sem código.",
            )
        }
        val statusRaw = args.optionalString("status")
        val statusId = if (statusRaw == null) {
            null
        } else {
            when (val resolved = ChatVocabularyResolver.resolveStatus(statusRaw, vocabulary.statuses)) {
                is ChatVocabularyResolver.StatusResolution.Found -> resolved.status.id
                is ChatVocabularyResolver.StatusResolution.Unknown ->
                    return errorJson(
                        "unknown_status",
                        "Unknown status '$statusRaw'. Valid names: ${resolved.validNames.joinToString()}",
                    )
            }
        }
        val created = createColmeia.create(
            CreateColmeiaCommand(
                userId = userId,
                speciesId = species.id,
                statusId = statusId,
                code = code,
                startDate = args.optionalInstant("startDate"),
            ),
        )
        return objectMapper.writeValueAsString(
            mapOf("colmeia" to created.toCompactMap(species.scientificName)),
        )
    }

    private fun executeUpdate(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val code = args.optionalInt("code")
            ?: return errorJson("missing_code", "code is required")
        val statusRaw = args.optionalString("status")
            ?: return errorJson("missing_status", "status (name) is required")
        val vocabulary = vocabularyUseCase.list()
        val status = when (val resolved = ChatVocabularyResolver.resolveStatus(statusRaw, vocabulary.statuses)) {
            is ChatVocabularyResolver.StatusResolution.Found -> resolved.status
            is ChatVocabularyResolver.StatusResolution.Unknown ->
                return errorJson(
                    "unknown_status",
                    "Unknown status '$statusRaw'. Valid names: ${resolved.validNames.joinToString()}",
                )
        }
        val hiveOutcome = resolveHive(userId, code, args.optionalString("currentStatus"))
        if (hiveOutcome is ResolveHive.Failed) return hiveOutcome.errorJson
        val hive = (hiveOutcome as ResolveHive.Ok).value
        if (hive.inactive && !args.path("confirmed").asBoolean(false)) {
            return errorJson(
                "confirmation_required",
                "A colmeia $code está ${hive.statusName}. Confirme para alterar.",
            )
        }
        val updated = recordColmeiaStatus.record(
            RecordColmeiaStatusCommand(
                userId = userId,
                colmeiaId = hive.colmeiaId,
                statusId = status.id,
                note = args.optionalString("note"),
            ),
        )
        val scientificName = vocabulary.species.firstOrNull { it.id == updated.speciesId }?.scientificName
        return objectMapper.writeValueAsString(
            mapOf("colmeia" to updated.toCompactMap(scientificName)),
        )
    }

    private fun executeDelete(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        if (!args.path("confirmed").asBoolean(false)) {
            throw ColmeiaCommandError.ConfirmationRequired()
        }
        val code = args.optionalInt("code")
            ?: return errorJson("missing_code", "code is required")
        val hiveOutcome = resolveHive(userId, code, args.optionalString("status"))
        if (hiveOutcome is ResolveHive.Failed) return hiveOutcome.errorJson
        val hive = (hiveOutcome as ResolveHive.Ok).value
        val deleted = deleteColmeia.delete(DeleteColmeiaCommand(userId = userId, colmeiaId = hive.colmeiaId))
        return objectMapper.writeValueAsString(
            mapOf(
                "deleted" to true,
                "code" to deleted.code,
                "species" to deleted.speciesCommonName,
                "status" to deleted.statusName,
            ),
        )
    }

    private sealed class ResolveHive {
        data class Ok(val value: ResolvedColmeia) : ResolveHive()
        data class Failed(val errorJson: String) : ResolveHive()
    }

    private fun resolveHive(userId: Long, code: Int, statusRaw: String?): ResolveHive {
        val statusId = if (statusRaw == null) {
            null
        } else {
            val vocabulary = vocabularyUseCase.list()
            when (val resolved = ChatVocabularyResolver.resolveStatus(statusRaw, vocabulary.statuses)) {
                is ChatVocabularyResolver.StatusResolution.Found -> resolved.status.id
                is ChatVocabularyResolver.StatusResolution.Unknown ->
                    return ResolveHive.Failed(
                        errorJson(
                            "unknown_status",
                            "Unknown status '$statusRaw'. Valid names: ${resolved.validNames.joinToString()}",
                        ),
                    )
            }
        }
        return ResolveHive.Ok(
            resolveColmeiaId.resolve(ResolveColmeiaQuery(userId = userId, code = code, statusId = statusId)),
        )
    }

    private fun ColmeiaSummary.toCompactMap(speciesScientificName: String?): Map<String, Any?> = mapOf(
        "code" to code,
        "species" to speciesCommonName,
        "speciesScientificName" to speciesScientificName,
        "status" to statusName,
    )

    /** Vocabulary scientific name when the common name uniquely matches one species. */
    private fun scientificNameForCommonName(commonName: String): String? {
        val matches = vocabularyUseCase.list().species.filter {
            it.commonName.equals(commonName, ignoreCase = true)
        }
        return matches.singleOrNull()?.scientificName
    }

    private sealed class ResolveOutcome<out T> {
        data class Ok<T>(val value: T) : ResolveOutcome<T>()
        data class Failed(val errorJson: String) : ResolveOutcome<Nothing>()
    }

    private fun resolveOptionalSpecies(
        raw: String?,
        vocabulary: ColmeiaVocabulary,
    ): ResolveOutcome<SpeciesRef?> {
        if (raw == null) return ResolveOutcome.Ok(null)
        return when (val resolved = ChatVocabularyResolver.resolveSpecies(raw, vocabulary.species)) {
            is ChatVocabularyResolver.SpeciesResolution.Found -> ResolveOutcome.Ok(resolved.species)
            is ChatVocabularyResolver.SpeciesResolution.Unknown ->
                ResolveOutcome.Failed(
                    errorJson(
                        "unknown_species",
                        "Unknown species '$raw'. Valid common names: ${resolved.validCommonNames.joinToString()}",
                    ),
                )
            is ChatVocabularyResolver.SpeciesResolution.Ambiguous ->
                ResolveOutcome.Failed(ambiguousSpeciesJson(resolved.input, resolved.matches))
        }
    }

    private fun resolveOptionalStatus(
        raw: String?,
        vocabulary: ColmeiaVocabulary,
    ): ResolveOutcome<StatusRef?> {
        if (raw == null) return ResolveOutcome.Ok(null)
        return when (val resolved = ChatVocabularyResolver.resolveStatus(raw, vocabulary.statuses)) {
            is ChatVocabularyResolver.StatusResolution.Found -> ResolveOutcome.Ok(resolved.status)
            is ChatVocabularyResolver.StatusResolution.Unknown ->
                ResolveOutcome.Failed(
                    errorJson(
                        "unknown_status",
                        "Unknown status '$raw'. Valid names: ${resolved.validNames.joinToString()}",
                    ),
                )
        }
    }

    private fun ambiguousSpeciesJson(input: String, matches: List<SpeciesRef>): String {
        val detail = matches.joinToString { "${it.commonName} (${it.abbreviation} / ${it.scientificName})" }
        return errorJson(
            "ambiguous_species",
            "Species '$input' matches multiple entries: $detail. Use scientific name or abbreviation.",
        )
    }

    private fun errorJson(code: String, message: String): String =
        objectMapper.writeValueAsString(mapOf("error" to code, "message" to message))

    private fun JsonNode.optionalString(field: String): String? {
        val node = path(field)
        return when {
            node.isNull || node.isMissingNode -> null
            node.isTextual -> node.asText().trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            node.isNumber -> node.asText() // reject numeric ids from older model habits by treating as unknown name
            else -> null
        }
    }

    private fun JsonNode.optionalInt(field: String): Int? {
        val node = path(field)
        return when {
            node.isNull || node.isMissingNode || node.isTextual && node.asText().equals("null", ignoreCase = true) -> null
            node.isNumber -> node.asInt()
            node.isTextual && node.asText().isNotBlank() -> node.asText().toIntOrNull()
            else -> null
        }
    }

    private fun JsonNode.optionalInstant(field: String): Instant? {
        val node = path(field)
        if (node.isNull || node.isMissingNode) return null
        val text = node.asText().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return null
        return runCatching { Instant.parse(text) }.getOrNull()
    }

    private companion object {
        val COUNT_COLMEIAS_DEF = ToolDefinition(
            name = ChatToolNames.COUNT_COLMEIAS,
            description =
                "Count the customer's hives (colmeias). Optional species/status filters by vocabulary names " +
                    "(common name or abbreviation for species; status name). A named status, including " +
                    "vendida or perdida, counts only that status: total is that count, and the default " +
                    "exclusion does not apply. Pass vendidas as vendida. Omit groupBy for a single-status " +
                    "question. On unknown_status do not retry without the status. Never invent names or pass " +
                    "numeric ids. Numbers and labels in the tool JSON are authoritative — restate them.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "species" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "Species common name (pt-BR) or abbreviation from list_vocabulary, or null",
                    ),
                    "status" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "Status name from list_vocabulary, or null",
                    ),
                    "groupBy" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string", "enum" to listOf("SPECIES", "STATUS")),
                        "description" to "Optional breakdown dimensions",
                    ),
                ),
                "additionalProperties" to false,
            ),
        )

        val LIST_VOCABULARY_DEF = ToolDefinition(
            name = ChatToolNames.LIST_VOCABULARY,
            description =
                "List valid species common names / abbreviations / scientific names and status names " +
                    "for filters and create/update. Use these labels in other tools — never numeric ids.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        )

        val DETAIL_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.DETAIL_COLMEIA,
            description =
                "Detalhar one hive: code, species common name, speciesScientificName when known, " +
                    "current status, and last visit. The user gives a code; this tool resolves it to one hive. " +
                    "Prefer the active hive when the code has one. Optional status selects the inactive hive " +
                    "that shares the code with a live one. A read does not ask for confirmation. " +
                    "Never pass numeric ids. Do not say listar.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "code" to mapOf("type" to "integer", "description" to "Hive code"),
                    "status" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "Current status name only when selecting the inactive hive that shares the code",
                    ),
                ),
                "required" to listOf("code"),
                "additionalProperties" to false,
            ),
        )

        val LIST_COLMEIA_HISTORY_DEF = ToolDefinition(
            name = ChatToolNames.LIST_COLMEIA_HISTORY,
            description =
                "Last N field visits (histórico) for one hive by code (date, status, optional note). " +
                    "lines are chronological (older first). Prefer the active hive when the code has one. " +
                    "Paste the lines array as returned. Never pass numeric ids.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "code" to mapOf("type" to "integer", "description" to "Hive code"),
                    "status" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "Current status name only when selecting the inactive hive that shares the code",
                    ),
                    "limit" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Max visits to return (1-50, default 10)",
                    ),
                ),
                "required" to listOf("code"),
                "additionalProperties" to false,
            ),
        )

        val CREATE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.CREATE_COLMEIA,
            description =
                "Cadastro: register a hive for the linked customer. Prefer asking for a código. " +
                    "species (common name) required from list_vocabulary. Result includes " +
                    "speciesScientificName when known — restate it with the common name. When the user " +
                    "gives a code, create in that turn. When code is omitted or null, do not create yet: " +
                    "ask once whether to cadastrar sem código, then call again with confirmed=true and " +
                    "code=null. startDate only if the user provided one (otherwise omit; server defaults " +
                    "to now). status defaults to desenvolvendo. Restate only tool JSON fields — if code " +
                    "is null say sem código; never invent a code. Never pass numeric ids.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "species" to mapOf(
                        "type" to "string",
                        "description" to "Required species common name (or abbreviation) from list_vocabulary",
                    ),
                    "status" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "Optional status name; default desenvolvendo",
                    ),
                    "code" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Hive code if the user provided one; null only after confirmed sem código",
                    ),
                    "startDate" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "ISO-8601 instant if the user provided one; otherwise omit",
                    ),
                    "confirmed" to mapOf(
                        "type" to "boolean",
                        "description" to
                            "True only after the user confirmed cadastrar sem código (required when code is null)",
                    ),
                ),
                "required" to listOf("species"),
                "additionalProperties" to false,
            ),
        )

        val UPDATE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.UPDATE_COLMEIA,
            description =
                "Record a field visit on one hive. The user gives a code; the write uses the resolved hive id. " +
                    "Prefer the active hive. A stated status on an active hive, including perdida or vendida, " +
                    "is recorded in the same turn with no confirmation. When the resolved hive is already " +
                    "inactive, ask once naming that status and call again with confirmed=true. Historico is " +
                    "appended by the use case, including when the status did not change; that is not a question. " +
                    "Do not call create_colmeia for a status change. Do not prefer this tool over delete. " +
                    "Never pass numeric ids.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "code" to mapOf("type" to "integer", "description" to "Hive code"),
                    "status" to mapOf(
                        "type" to "string",
                        "description" to "Observed status name (appends historico, including same status)",
                    ),
                    "note" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to
                            "Optional short field remark only if the user supplied one; otherwise null",
                    ),
                    "currentStatus" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to
                            "Current status name only when the user means the inactive hive that shares the code with an active one",
                    ),
                    "confirmed" to mapOf(
                        "type" to "boolean",
                        "description" to "True only after the user confirmed a change to an already inactive hive",
                    ),
                ),
                "required" to listOf("code", "status"),
                "additionalProperties" to false,
            ),
        )

        val DELETE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.DELETE_COLMEIA,
            description =
                "Permanently delete one hive (active or already perdida/vendida — same flow). Prefer the " +
                    "active hive when the code is ambiguous. Ask once for confirmation, then call with " +
                    "confirmed=true; the write uses the resolved hive id. On that first confirmation " +
                    "turn in the conversation, mention perdida/vendida as an optional alternative; do " +
                    "not keep re-suggesting soft exit. Optional status selects the inactive hive when " +
                    "an active hive shares the code. Do not redirect excluir into perdida or vendida. " +
                    "Never pass numeric ids.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "code" to mapOf("type" to "integer", "description" to "Hive code to delete"),
                    "status" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "Current status name when deleting the inactive hive that shares the code",
                    ),
                    "confirmed" to mapOf(
                        "type" to "boolean",
                        "description" to "Must be true after user confirmed this final delete",
                    ),
                ),
                "required" to listOf("code", "confirmed"),
                "additionalProperties" to false,
            ),
        )
    }
}

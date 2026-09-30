package com.bombus.chatbot.application

import com.bombus.chatbot.domain.AssistantToolCall
import com.bombus.chatbot.domain.ToolDefinition
import com.bombus.chatbot.domain.ToolResultMessage
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.CreateColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaUseCase
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Builds tool definitions and executes tool calls against colmeia use cases.
 * Counts and hive mutations come only from use cases; the model phrases replies.
 */
@Component
class ChatToolExecutor(
    private val countColmeias: CountColmeiasUseCase,
    private val vocabularyUseCase: ListColmeiaVocabularyUseCase,
    private val listOwnedColmeias: ListOwnedColmeiasUseCase,
    private val createColmeia: CreateColmeiaUseCase,
    private val updateColmeia: UpdateColmeiaUseCase,
    private val softDeleteColmeia: SoftDeleteColmeiaUseCase,
    private val objectMapper: ObjectMapper,
) {

    fun definitions(): List<ToolDefinition> = listOf(
        COUNT_COLMEIAS_DEF,
        LIST_VOCABULARY_DEF,
        LIST_COLMEIAS_DEF,
        CREATE_COLMEIA_DEF,
        UPDATE_COLMEIA_DEF,
        SOFT_DELETE_COLMEIA_DEF,
    )

    fun execute(userId: Long, call: AssistantToolCall): ToolResultMessage {
        val contentJson = try {
            when (call.name) {
                ChatToolNames.COUNT_COLMEIAS -> executeCount(userId, call.argumentsJson)
                ChatToolNames.LIST_VOCABULARY -> executeListVocabulary()
                ChatToolNames.LIST_COLMEIAS -> executeList(userId, call.argumentsJson)
                ChatToolNames.CREATE_COLMEIA -> executeCreate(userId, call.argumentsJson)
                ChatToolNames.UPDATE_COLMEIA -> executeUpdate(userId, call.argumentsJson)
                ChatToolNames.SOFT_DELETE_COLMEIA -> executeSoftDelete(userId, call.argumentsJson)
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
        val speciesId = args.optionalLong("speciesId")
        val statusId = args.optionalLong("statusId")
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
                speciesId = speciesId,
                statusId = statusId,
                groupBy = groupBy,
            ),
        )

        val vocabulary = vocabularyUseCase.list()
        val speciesLabel = speciesId?.let { id -> vocabulary.species.firstOrNull { it.id == id }?.commonName }
        val statusLabel = statusId?.let { id -> vocabulary.statuses.firstOrNull { it.id == id }?.name }

        val payload = linkedMapOf<String, Any?>(
            "total" to count.total,
            "speciesId" to speciesId,
            "statusId" to statusId,
            "speciesLabel" to speciesLabel,
            "statusLabel" to statusLabel,
            "groupBy" to groupBy.map { it.name },
        )
        count.perSpecies?.let { breakdown ->
            payload["perSpecies"] = breakdown.map {
                mapOf(
                    "speciesId" to it.speciesId,
                    "abbreviation" to it.abbreviation,
                    "commonName" to it.commonName,
                    "count" to it.count,
                )
            }
        }
        count.perStatus?.let { breakdown ->
            payload["perStatus"] = breakdown.map {
                mapOf(
                    "statusId" to it.statusId,
                    "statusName" to it.statusName,
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
                    "id" to it.id,
                    "abbreviation" to it.abbreviation,
                    "commonName" to it.commonName,
                    "scientificName" to it.scientificName,
                )
            },
            "statuses" to vocabulary.statuses.map {
                mapOf("id" to it.id, "name" to it.name)
            },
        )
        return objectMapper.writeValueAsString(payload)
    }

    private fun executeList(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val items = listOwnedColmeias.list(
            ListOwnedColmeiasQuery(
                userId = userId,
                includeLost = args.path("includeLost").asBoolean(false),
                limit = args.optionalInt("limit") ?: ListOwnedColmeiasQuery.DEFAULT_LIMIT,
                offset = args.optionalInt("offset") ?: 0,
            ),
        )
        return objectMapper.writeValueAsString(
            mapOf(
                "count" to items.size,
                "items" to items.map { it.toCompactMap() },
            ),
        )
    }

    private fun executeCreate(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val speciesId = args.optionalLong("speciesId")
            ?: return errorJson("missing_speciesId", "speciesId is required")
        val created = createColmeia.create(
            CreateColmeiaCommand(
                userId = userId,
                speciesId = speciesId,
                statusId = args.optionalLong("statusId"),
                code = args.optionalInt("code"),
                meliponarioId = args.optionalLong("meliponarioId"),
                startDate = args.optionalInstant("startDate"),
            ),
        )
        return objectMapper.writeValueAsString(mapOf("colmeia" to created.toCompactMap()))
    }

    private fun executeUpdate(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val updated = updateColmeia.update(
            UpdateColmeiaCommand(
                userId = userId,
                colmeiaId = args.optionalLong("colmeiaId"),
                code = args.optionalInt("code"),
                meliponarioId = args.optionalLong("meliponarioId"),
                speciesId = args.optionalLong("speciesId"),
                statusId = args.optionalLong("statusId"),
                startDate = args.optionalInstant("startDate"),
            ),
        )
        return objectMapper.writeValueAsString(mapOf("colmeia" to updated.toCompactMap()))
    }

    private fun executeSoftDelete(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val deleted = softDeleteColmeia.softDelete(
            SoftDeleteColmeiaCommand(
                userId = userId,
                colmeiaId = args.optionalLong("colmeiaId"),
                code = args.optionalInt("code"),
                meliponarioId = args.optionalLong("meliponarioId"),
            ),
        )
        return objectMapper.writeValueAsString(
            mapOf(
                "softDeleted" to true,
                "colmeia" to deleted.toCompactMap(),
            ),
        )
    }

    private fun ColmeiaSummary.toCompactMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "code" to code,
        "speciesId" to speciesId,
        "species" to speciesAbbreviation,
        "speciesName" to speciesCommonName,
        "statusId" to statusId,
        "status" to statusName,
        "meliponarioId" to meliponarioId,
        "startDate" to startDate?.toString(),
    )

    private fun errorJson(code: String, message: String): String =
        objectMapper.writeValueAsString(mapOf("error" to code, "message" to message))

    private fun JsonNode.optionalLong(field: String): Long? {
        val node = path(field)
        return when {
            node.isNull || node.isMissingNode || node.isTextual && node.asText().equals("null", ignoreCase = true) -> null
            node.isNumber -> node.asLong()
            node.isTextual && node.asText().isNotBlank() -> node.asText().toLongOrNull()
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
                "Count the customer's hives (colmeias). Optional speciesId/statusId filter by vocabulary ids. " +
                    "Optional groupBy SPECIES and/or STATUS for breakdowns. Numbers are authoritative — restate them.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "speciesId" to mapOf("type" to listOf("integer", "null"), "description" to "Species id from list_vocabulary, or null"),
                    "statusId" to mapOf("type" to listOf("integer", "null"), "description" to "Status id from list_vocabulary, or null"),
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
            description = "List valid species and status ids/names for filters and create/update.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        )

        val LIST_COLMEIAS_DEF = ToolDefinition(
            name = ChatToolNames.LIST_COLMEIAS,
            description =
                "List the customer's colmeias concisely (code, species, status, id). " +
                    "Excludes perdida by default. Use limit/offset for short WhatsApp replies.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "includeLost" to mapOf(
                        "type" to "boolean",
                        "description" to "If true, include soft-deleted (perdida) hives",
                    ),
                    "limit" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Max items (1-50, default 20)",
                    ),
                    "offset" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Pagination offset (default 0)",
                    ),
                ),
                "additionalProperties" to false,
            ),
        )

        val CREATE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.CREATE_COLMEIA,
            description =
                "Create a hive for the linked customer. speciesId required (from list_vocabulary). " +
                    "code auto-assigned next free (non-perdida) unless provided. " +
                    "status defaults to estavel. meliponario defaults to customer's oldest. startDate defaults to now.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "speciesId" to mapOf("type" to "integer", "description" to "Required species id"),
                    "statusId" to mapOf("type" to listOf("integer", "null"), "description" to "Optional status id; default estavel"),
                    "code" to mapOf("type" to listOf("integer", "null"), "description" to "Optional hive code; must be free among non-perdida"),
                    "meliponarioId" to mapOf("type" to listOf("integer", "null"), "description" to "Optional owned meliponário id"),
                    "startDate" to mapOf("type" to listOf("string", "null"), "description" to "Optional ISO-8601 instant"),
                ),
                "required" to listOf("speciesId"),
                "additionalProperties" to false,
            ),
        )

        val UPDATE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.UPDATE_COLMEIA,
            description =
                "Update an owned hive. Identify with colmeiaId or code (+ meliponarioId if ambiguous). " +
                    "May change speciesId, statusId (appends history), and/or startDate. Code is not changed.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "colmeiaId" to mapOf("type" to listOf("integer", "null"), "description" to "Hive id when known"),
                    "code" to mapOf("type" to listOf("integer", "null"), "description" to "Hive code if id unknown"),
                    "meliponarioId" to mapOf("type" to listOf("integer", "null"), "description" to "Disambiguate code across meliponários"),
                    "speciesId" to mapOf("type" to listOf("integer", "null"), "description" to "New species id"),
                    "statusId" to mapOf("type" to listOf("integer", "null"), "description" to "New status id (appends history)"),
                    "startDate" to mapOf("type" to listOf("string", "null"), "description" to "New ISO-8601 startDate"),
                ),
                "additionalProperties" to false,
            ),
        )

        val SOFT_DELETE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.SOFT_DELETE_COLMEIA,
            description =
                "Soft-delete an owned hive by appending status perdida (never hard-delete). " +
                    "Frees the hive code for reuse. Identify with colmeiaId or code. Idempotent if already perdida.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "colmeiaId" to mapOf("type" to listOf("integer", "null"), "description" to "Hive id when known"),
                    "code" to mapOf("type" to listOf("integer", "null"), "description" to "Hive code if id unknown"),
                    "meliponarioId" to mapOf("type" to listOf("integer", "null"), "description" to "Disambiguate code"),
                ),
                "additionalProperties" to false,
            ),
        )
    }
}

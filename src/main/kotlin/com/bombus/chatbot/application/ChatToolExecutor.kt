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
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaUseCase
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.SpeciesRef
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
    private val deleteColmeia: DeleteColmeiaUseCase,
    private val objectMapper: ObjectMapper,
    private val countProperties: ColmeiaCountProperties,
) {

    fun definitions(): List<ToolDefinition> = listOf(
        COUNT_COLMEIAS_DEF,
        LIST_VOCABULARY_DEF,
        LIST_COLMEIAS_DEF,
        CREATE_COLMEIA_DEF,
        UPDATE_COLMEIA_DEF,
        DELETE_COLMEIA_DEF,
    )

    fun execute(userId: Long, call: AssistantToolCall): ToolResultMessage {
        val contentJson = try {
            when (call.name) {
                ChatToolNames.COUNT_COLMEIAS -> executeCount(userId, call.argumentsJson)
                ChatToolNames.LIST_VOCABULARY -> executeListVocabulary()
                ChatToolNames.LIST_COLMEIAS -> executeList(userId, call.argumentsJson)
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

        val vocabulary = vocabularyUseCase.list()
        val species: SpeciesRef? = speciesId?.let { id ->
            vocabulary.species.firstOrNull { it.id == id }
                ?: return errorJson("unknown_species_id", "Unknown speciesId: $id")
        }
        val statusLabel = statusId?.let { id ->
            vocabulary.statuses.firstOrNull { it.id == id }?.name
                ?: return errorJson("unknown_status_id", "Unknown statusId: $id")
        }

        val count = countColmeias.count(
            CountColmeiasQuery(
                userId = userId,
                speciesId = speciesId,
                statusId = statusId,
                groupBy = groupBy,
            ),
        )

        val payload = linkedMapOf<String, Any?>(
            "total" to count.total,
            "speciesId" to speciesId,
            "statusId" to statusId,
            "speciesLabel" to species?.commonName,
            "speciesScientificName" to species?.scientificName,
            "speciesAbbreviation" to species?.abbreviation,
            "statusLabel" to statusLabel,
            "excludedStatusLabel" to if (statusId == null) countProperties.defaultExcludedStatus else null,
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
        val includeLost = args.path("includeLost").asBoolean(false)
        val total = countColmeias.count(CountColmeiasQuery(userId = userId)).total
        if (!includeLost && total > SPECIES_COUNT_THRESHOLD) {
            val breakdown = countColmeias.count(
                CountColmeiasQuery(
                    userId = userId,
                    groupBy = setOf(CountDimension.SPECIES),
                ),
            )
            return objectMapper.writeValueAsString(
                mapOf(
                    "mode" to "species_count",
                    "total" to breakdown.total,
                    "perSpecies" to (breakdown.perSpecies?.map {
                        mapOf(
                            "commonName" to it.commonName,
                            "count" to it.count,
                        )
                    } ?: emptyList()),
                ),
            )
        }

        val items = listOwnedColmeias.list(
            ListOwnedColmeiasQuery(
                userId = userId,
                includeLost = includeLost,
                limit = args.optionalInt("limit") ?: ListOwnedColmeiasQuery.DEFAULT_LIMIT,
                offset = args.optionalInt("offset") ?: 0,
            ),
        )
        return objectMapper.writeValueAsString(
            mapOf(
                "mode" to "list",
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
                startDate = args.optionalInstant("startDate"),
            ),
        )
        return objectMapper.writeValueAsString(mapOf("colmeia" to created.toCompactMap()))
    }

    private fun executeUpdate(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        val code = args.optionalInt("code")
            ?: return errorJson("missing_code", "code is required")
        val statusId = args.optionalLong("statusId")
            ?: return errorJson("missing_statusId", "statusId is required")
        val updated = updateColmeia.update(
            UpdateColmeiaCommand(
                userId = userId,
                code = code,
                statusId = statusId,
            ),
        )
        return objectMapper.writeValueAsString(mapOf("colmeia" to updated.toCompactMap()))
    }

    private fun executeDelete(userId: Long, argumentsJson: String): String {
        val args = objectMapper.readTree(argumentsJson.ifBlank { "{}" })
        if (!args.path("confirmed").asBoolean(false)) {
            throw ColmeiaCommandError.ConfirmationRequired()
        }
        val code = args.optionalInt("code")
            ?: return errorJson("missing_code", "code is required")
        val deleted = deleteColmeia.delete(DeleteColmeiaCommand(userId = userId, code = code))
        return objectMapper.writeValueAsString(
            mapOf(
                "deleted" to true,
                "code" to deleted.code,
                "species" to deleted.speciesCommonName,
                "status" to deleted.statusName,
            ),
        )
    }

    private fun ColmeiaSummary.toCompactMap(): Map<String, Any?> = mapOf(
        "code" to code,
        "species" to speciesCommonName,
        "status" to statusName,
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
        const val SPECIES_COUNT_THRESHOLD = 10

        val COUNT_COLMEIAS_DEF = ToolDefinition(
            name = ChatToolNames.COUNT_COLMEIAS,
            description =
                "Count the customer's hives (colmeias). Pass speciesId/statusId only from list_vocabulary " +
                    "(match the user's words to vocabulary entries first). Never invent ids or reuse a count " +
                    "total/ordinal from chat as an id. Optional groupBy SPECIES and/or STATUS for breakdowns. " +
                    "Numbers and labels in the tool JSON are authoritative — restate them.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "speciesId" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Species id from list_vocabulary only, or null",
                    ),
                    "statusId" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Status id from list_vocabulary only, or null",
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
                "List the customer's colmeias (code, species common name, status). " +
                    "If the customer has more than 10 hives, returns per-species counts instead of a row list. " +
                    "Excludes perdida by default.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "includeLost" to mapOf(
                        "type" to "boolean",
                        "description" to "If true, include hives currently status perdida",
                    ),
                    "limit" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Max items when listing rows (1-50, default 20)",
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
                    "code and startDate only if the user provided them (otherwise null). " +
                    "status defaults to em_desenvolvimento.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "speciesId" to mapOf("type" to "integer", "description" to "Required species id"),
                    "statusId" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Optional status id; default em_desenvolvimento",
                    ),
                    "code" to mapOf(
                        "type" to listOf("integer", "null"),
                        "description" to "Hive code if the user provided one; otherwise null",
                    ),
                    "startDate" to mapOf(
                        "type" to listOf("string", "null"),
                        "description" to "ISO-8601 instant if the user provided one; otherwise null",
                    ),
                ),
                "required" to listOf("speciesId"),
                "additionalProperties" to false,
            ),
        )

        val UPDATE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.UPDATE_COLMEIA,
            description =
                "Update an owned hive status. Identify by code. Only statusId is mutable (appends history). " +
                    "Species, code, meliponário, and startDate stay fixed.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "code" to mapOf("type" to "integer", "description" to "Hive code"),
                    "statusId" to mapOf("type" to "integer", "description" to "New status id (appends history)"),
                ),
                "required" to listOf("code", "statusId"),
                "additionalProperties" to false,
            ),
        )

        val DELETE_COLMEIA_DEF = ToolDefinition(
            name = ChatToolNames.DELETE_COLMEIA,
            description =
                "Permanently delete an owned hive by code (hard delete, irreversible). " +
                    "Only call after the user explicitly confirmed. Pass confirmed=true.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "code" to mapOf("type" to "integer", "description" to "Hive code to delete"),
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

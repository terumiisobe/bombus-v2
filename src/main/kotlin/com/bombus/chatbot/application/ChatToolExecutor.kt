package com.bombus.chatbot.application

import com.bombus.chatbot.domain.AssistantToolCall
import com.bombus.chatbot.domain.ToolDefinition
import com.bombus.chatbot.domain.ToolResultMessage
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * Builds tool definitions and executes tool calls against colmeia use cases.
 * Numbers come only from [CountColmeiasUseCase]; JSON includes filter labels for zero-phrasing.
 */
@Component
class ChatToolExecutor(
    private val countColmeias: CountColmeiasUseCase,
    private val vocabularyUseCase: ListColmeiaVocabularyUseCase,
    private val objectMapper: ObjectMapper,
) {

    fun definitions(): List<ToolDefinition> = listOf(COUNT_COLMEIAS_DEF, LIST_VOCABULARY_DEF)

    fun execute(userId: Long, call: AssistantToolCall): ToolResultMessage {
        val contentJson = try {
            when (call.name) {
                ChatToolNames.COUNT_COLMEIAS -> executeCount(userId, call.argumentsJson)
                ChatToolNames.LIST_VOCABULARY -> executeListVocabulary()
                else -> errorJson("unknown_tool", "Unknown tool: ${call.name}")
            }
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
            description = "List valid species and status ids/names for counting filters.",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        )
    }
}

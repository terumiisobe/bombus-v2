package com.bombus.chatbot.adapter.outbound.external

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @JsonProperty("response_format") val responseFormat: ResponseFormat? = null,
)

internal data class ChatMessage(
    val role: String,
    val content: String,
)

internal data class ResponseFormat(
    val type: String,
    @JsonProperty("json_schema") val jsonSchema: JsonSchema? = null,
)

internal data class JsonSchema(
    val name: String,
    val strict: Boolean,
    val schema: Map<String, Any>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Choice(
    val message: ResponseMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ResponseMessage(
    val content: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ParsedIntentPayload(
    val intent: String,
    val speciesId: Long? = null,
    val statusId: Long? = null,
)

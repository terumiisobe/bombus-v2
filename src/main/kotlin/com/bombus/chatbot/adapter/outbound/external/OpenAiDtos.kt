package com.bombus.chatbot.adapter.outbound.external

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val tools: List<OpenAiTool>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class ChatMessage(
    val role: String,
    val content: String? = null,
    @JsonProperty("tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
    @JsonProperty("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunctionDef,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OpenAiFunctionDef(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunctionCall,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class OpenAiFunctionCall(
    val name: String,
    val arguments: String,
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
    @JsonProperty("tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
)

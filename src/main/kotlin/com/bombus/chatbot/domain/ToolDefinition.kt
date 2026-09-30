package com.bombus.chatbot.domain

/**
 * Provider-agnostic description of a tool the model may call.
 * [parametersJsonSchema] is a JSON Schema object (as a Map) for the tool arguments.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: Map<String, Any>,
)

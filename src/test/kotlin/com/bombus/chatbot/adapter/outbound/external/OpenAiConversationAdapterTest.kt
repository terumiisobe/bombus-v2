package com.bombus.chatbot.adapter.outbound.external

import com.bombus.chatbot.domain.AgentCompletion
import com.bombus.chatbot.domain.AgentMessage
import com.bombus.chatbot.domain.AssistantToolCall
import com.bombus.chatbot.domain.ToolDefinition
import com.bombus.chatbot.domain.ToolResultMessage
import com.bombus.config.OpenAiProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class OpenAiConversationAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenAiConversationAdapter

    private val tools = listOf(
        ToolDefinition(
            name = "count_colmeias",
            description = "Count hives",
            parametersJsonSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "speciesId" to mapOf("type" to listOf("integer", "null")),
                ),
            ),
        ),
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val properties = OpenAiProperties(
            apiKey = "test-key",
            model = "gpt-4o-mini",
            baseUrl = server.url("/v1").toString(),
        )
        adapter = OpenAiConversationAdapter(RestClient.builder().build(), properties)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `complete request includes tools and system prompt rules`() {
        enqueueFinalReply("Você tem 3 colmeias.")

        val result = adapter.complete(listOf(AgentMessage.User("quantas colmeias?")), tools)

        assertThat(result).isEqualTo(AgentCompletion.FinalReply("Você tem 3 colmeias."))

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/v1/chat/completions")
        val body = objectMapper.readTree(request.body.readUtf8())
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.0)
        assertThat(body.path("tools").path(0).path("function").path("name").asText())
            .isEqualTo("count_colmeias")
        val system = body.path("messages").path(0)
        assertThat(system.path("role").asText()).isEqualTo("system")
        assertThat(system.path("content").asText())
            .containsIgnoringCase("nunca invente")
            .contains("pt-BR")
            .contains("speciesLabel")
    }

    @Test
    fun `complete maps tool_calls from the model response`() {
        enqueueToolCalls(
            """
            [{"id":"call_abc","type":"function","function":{"name":"count_colmeias","arguments":"{\"statusId\":3}"}}]
            """.trimIndent(),
        )

        val result = adapter.complete(listOf(AgentMessage.User("quantas estáveis?")), tools)

        assertThat(result).isEqualTo(
            AgentCompletion.ToolCalls(
                listOf(
                    AssistantToolCall(
                        id = "call_abc",
                        name = "count_colmeias",
                        argumentsJson = """{"statusId":3}""",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `complete round-trips tool role messages`() {
        enqueueFinalReply("Zero estáveis; outras colmeias podem existir.")

        adapter.complete(
            listOf(
                AgentMessage.User("quantas estáveis por espécie?"),
                AgentMessage.Assistant(
                    toolCalls = listOf(
                        AssistantToolCall(
                            id = "call_1",
                            name = "count_colmeias",
                            argumentsJson = """{"statusId":3,"groupBy":["SPECIES"]}""",
                        ),
                    ),
                ),
                AgentMessage.Tool(
                    ToolResultMessage(
                        toolCallId = "call_1",
                        name = "count_colmeias",
                        contentJson = """{"total":0,"statusLabel":"estavel"}""",
                    ),
                ),
            ),
            tools,
        )

        val body = objectMapper.readTree(server.takeRequest().body.readUtf8())
        val roles = body.path("messages").map { it.path("role").asText() }
        assertThat(roles).containsExactly("system", "user", "assistant", "tool")

        val assistant = body.path("messages").path(2)
        assertThat(assistant.path("tool_calls").path(0).path("id").asText()).isEqualTo("call_1")
        assertThat(assistant.path("tool_calls").path(0).path("function").path("name").asText())
            .isEqualTo("count_colmeias")

        val tool = body.path("messages").path(3)
        assertThat(tool.path("tool_call_id").asText()).isEqualTo("call_1")
        assertThat(tool.path("content").asText()).contains("statusLabel")
    }

    @Test
    fun `complete returns Failed when the model call fails`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = adapter.complete(listOf(AgentMessage.User("oi")), tools)

        assertThat(result).isInstanceOf(AgentCompletion.Failed::class.java)
    }

    @Test
    fun `complete returns Failed when content is blank and there are no tool calls`() {
        enqueueFinalReply("   ")

        val result = adapter.complete(listOf(AgentMessage.User("oi")), tools)

        assertThat(result).isEqualTo(AgentCompletion.Failed("blank_content"))
    }

    private fun enqueueFinalReply(content: String) {
        val body = """{"choices":[{"message":{"role":"assistant","content":${objectMapper.writeValueAsString(content)}}}]}"""
        server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "application/json"))
    }

    private fun enqueueToolCalls(toolCallsJson: String) {
        val body = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":$toolCallsJson}}]}"""
        server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "application/json"))
    }
}

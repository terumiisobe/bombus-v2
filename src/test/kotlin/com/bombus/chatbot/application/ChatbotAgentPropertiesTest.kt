package com.bombus.chatbot.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChatbotAgentPropertiesTest {

    @Test
    fun `default fallbackReply briefly lists colmeia capabilities without pushy closers`() {
        val reply = ChatbotAgentProperties().fallbackReply

        assertThat(reply)
            .contains("listar")
            .contains("cadastrar")
            .contains("visita de status")
            .contains("perdida/vendida")
            .contains("erro de cadastro")
            .contains("contar")
            .doesNotContain("é só me avisar")
            .doesNotContain("se precisar de mais informações")
            .doesNotContain("experimente perguntar")
    }
}

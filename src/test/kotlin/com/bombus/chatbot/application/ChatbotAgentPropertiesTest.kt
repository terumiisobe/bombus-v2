package com.bombus.chatbot.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChatbotAgentPropertiesTest {

    @Test
    fun `default fallbackReply briefly lists colmeia capabilities without pushy closers`() {
        val reply = ChatbotAgentProperties().fallbackReply

        assertThat(reply)
            .contains("detalhar")
            .contains("histórico de visitas")
            .contains("cadastrar")
            .contains("registrar visita")
            .contains("perdida ou vendida")
            .contains("excluir")
            .contains("contar")
            .doesNotContain("listar")
            .doesNotContain("erro de cadastro")
            .doesNotContain("é só me avisar")
            .doesNotContain("se precisar de mais informações")
            .doesNotContain("experimente perguntar")
    }
}

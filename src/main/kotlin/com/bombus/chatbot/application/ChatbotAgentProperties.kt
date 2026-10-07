package com.bombus.chatbot.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chatbot.agent")
data class ChatbotAgentProperties(
    val maxToolRounds: Int = 3,
    val fallbackReply: String =
        "Desculpe, não consegui processar sua mensagem agora. " +
            "Posso listar, ver histórico de visitas, cadastrar, registrar visita de status, " +
            "marcar perdida/vendida ou excluir só por erro de cadastro, e contar suas colmeias.",
)

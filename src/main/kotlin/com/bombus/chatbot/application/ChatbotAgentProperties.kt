package com.bombus.chatbot.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chatbot.agent")
data class ChatbotAgentProperties(
    val maxToolRounds: Int = 3,
    val fallbackReply: String =
        "Desculpe, não consegui processar sua mensagem agora. " +
            "Posso listar, adicionar, excluir, atualizar o status e contar suas colmeias.",
)

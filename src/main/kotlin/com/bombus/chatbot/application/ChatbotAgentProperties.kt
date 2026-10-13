package com.bombus.chatbot.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chatbot.agent")
data class ChatbotAgentProperties(
    val maxToolRounds: Int = 3,
    val fallbackReply: String =
        "Desculpe, não consegui processar sua mensagem agora. " +
            "Posso detalhar uma colmeia, ver histórico de visitas, cadastrar, registrar visita, " +
            "marcar perdida ou vendida, excluir e contar suas colmeias.",
)

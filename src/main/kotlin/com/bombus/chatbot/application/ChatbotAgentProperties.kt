package com.bombus.chatbot.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chatbot.agent")
data class ChatbotAgentProperties(
    val maxToolRounds: Int = 2,
    val fallbackReply: String =
        "Desculpe, não consegui processar sua mensagem agora. " +
            "Posso contar suas colmeias — experimente perguntar quantas você tem, por espécie ou por status.",
)

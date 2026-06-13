package com.bombus.chatbot.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface UsuarioWhatsappJpaRepository : JpaRepository<UsuarioWhatsappEntity, Long> {

    fun findByPhoneNumberAndActiveTrue(phoneNumber: String): UsuarioWhatsappEntity?
}

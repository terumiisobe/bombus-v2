package com.bombus.chatbot.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * JPA mapping for the usuario_whatsapp link table. Stays inside the persistence adapter and
 * is never exposed to the application/domain; the adapter maps it to the domain Customer.
 *
 * Regular class (not a data class) per the JPA conventions; equality is based on the stable
 * business key (phone_number, which is UNIQUE) rather than generated data-class equality.
 */
@Entity
@Table(name = "usuario_whatsapp")
class UsuarioWhatsappEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "usuario_id", nullable = false)
    val usuarioId: Long = 0,

    @Column(name = "phone_number", nullable = false, unique = true)
    val phoneNumber: String = "",

    @Column(name = "display_name")
    val displayName: String? = null,

    @Column(name = "active", nullable = false)
    val active: Boolean = true,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UsuarioWhatsappEntity) return false
        return phoneNumber == other.phoneNumber
    }

    override fun hashCode(): Int = phoneNumber.hashCode()
}

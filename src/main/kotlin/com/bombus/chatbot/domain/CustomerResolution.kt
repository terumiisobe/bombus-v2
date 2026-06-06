package com.bombus.chatbot.domain

/**
 * Outcome of resolving an inbound phone number to a customer. A sender that is unknown
 * or whose link is inactive is an expected outcome ([NotLinked]), not an error.
 */
sealed interface CustomerResolution {

    data class Resolved(val customer: Customer) : CustomerResolution

    data object NotLinked : CustomerResolution
}

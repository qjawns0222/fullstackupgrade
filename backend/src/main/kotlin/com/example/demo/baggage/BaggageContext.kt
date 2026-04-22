package com.example.demo.baggage

data class BaggageContext(
    val userId: String?,
    val tenantId: String?
) {
    fun isPresent(): Boolean = userId != null || tenantId != null

    companion object {
        val EMPTY = BaggageContext(userId = null, tenantId = null)
    }
}

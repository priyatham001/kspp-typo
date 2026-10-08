package com.example.auth

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val status: String = STATUS_PENDING,
    val role: String = ROLE_USER,
    val registrationDate: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val accessExpiresAt: Long = 0L
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_SUSPENDED = "SUSPENDED"
        const val STATUS_TERMINATED = "TERMINATED"

        const val ROLE_USER = "USER"
        const val ROLE_ADMIN = "ADMIN"
        const val ROLE_SUPER_ADMIN = "SUPER_ADMIN"

        const val EIGHT_HOURS_MILLIS = 8 * 60 * 60 * 1000L
    }

    val isApproved: Boolean get() = status == STATUS_APPROVED
    val isAdmin: Boolean get() = role == ROLE_ADMIN || role == ROLE_SUPER_ADMIN
    val isSuperAdmin: Boolean get() = role == ROLE_SUPER_ADMIN

    val isAccessExpired: Boolean get() {
        if (isAdmin) return false
        return accessExpiresAt > 0L && System.currentTimeMillis() > accessExpiresAt
    }

    val hasActiveAccess: Boolean get() {
        return isApproved && !isAccessExpired
    }

    fun getRemainingTimeFormatted(): String {
        if (isAdmin || accessExpiresAt == 0L) return "Permanent (Admin)"
        val remaining = accessExpiresAt - System.currentTimeMillis()
        if (remaining <= 0) return "Expired"
        val hours = remaining / (1000 * 60 * 60)
        val minutes = (remaining / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
    }
}

data class ServiceControl(
    val serviceEnabled: Boolean = true,
    val disabledMessage: String = "REPLICA service is temporarily unavailable.",
    val updatedBy: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class AuditLog(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val adminId: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetUserId: String? = null,
    val result: String = "SUCCESS"
)

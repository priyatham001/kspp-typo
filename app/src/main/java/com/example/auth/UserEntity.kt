package com.example.auth

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    USER, ADMIN, SUPER_ADMIN
}

enum class AccountStatus {
    PENDING, APPROVED, SUSPENDED, TERMINATED
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val role: String = UserRole.USER.name,
    val status: String = AccountStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

package com.example.auth

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val adminEmail: String,
    val action: String,
    val targetEmail: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)

package com.example.auth

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "AdminRepository"

class AdminRepository(
    private val firestore: FirebaseFirestore
) {

    constructor(context: Context) : this(
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun getAllUsersFlow(): Flow<List<UserProfile>> = callbackFlow {
        val listener = firestore.collection("users")
            .orderBy("registrationDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error fetching users: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { doc ->
                        UserProfile(
                            userId = doc.getString("userId") ?: doc.id,
                            displayName = doc.getString("displayName") ?: "",
                            email = doc.getString("email") ?: "",
                            photoUrl = doc.getString("photoUrl"),
                            status = doc.getString("status") ?: UserProfile.STATUS_PENDING,
                            role = doc.getString("role") ?: UserProfile.ROLE_USER,
                            registrationDate = doc.getLong("registrationDate") ?: 0L,
                            lastLogin = doc.getLong("lastLogin") ?: 0L,
                            accessExpiresAt = doc.getLong("accessExpiresAt") ?: 0L,
                            adminComment = doc.getString("adminComment"),
                            adminCommentBy = doc.getString("adminCommentBy"),
                            adminCommentAt = doc.getLong("adminCommentAt") ?: 0L
                        )
                    }
                    trySend(users)
                }
            }

        awaitClose { listener.remove() }
    }

    fun getAuditLogsFlow(): Flow<List<AuditLog>> = callbackFlow {
        val listener = firestore.collection("audit_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error fetching audit logs: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val logs = snapshot.documents.mapNotNull { doc ->
                        AuditLog(
                            id = doc.id,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            adminId = doc.getString("adminId") ?: "",
                            adminEmail = doc.getString("adminEmail") ?: "",
                            action = doc.getString("action") ?: "",
                            targetUserId = doc.getString("targetUserId"),
                            result = doc.getString("result") ?: "SUCCESS"
                        )
                    }
                    trySend(logs)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun approveUser(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        val newExpiry = System.currentTimeMillis() + UserProfile.EIGHT_HOURS_MILLIS
        return try {
            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "status" to UserProfile.STATUS_APPROVED,
                        "accessExpiresAt" to newExpiry
                    )
                ).await()
            recordAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "USER_APPROVED_8H",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extendUserAccess(userId: String, additionalHours: Int = 8, adminId: String, adminEmail: String): Result<Unit> {
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val currentExpiry = userDoc.getLong("accessExpiresAt") ?: 0L
            val now = System.currentTimeMillis()
            val baseTime = if (currentExpiry > now) currentExpiry else now
            val newExpiry = baseTime + (additionalHours * 60 * 60 * 1000L)

            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "status" to UserProfile.STATUS_APPROVED,
                        "accessExpiresAt" to newExpiry
                    )
                ).await()

            recordAuditLog(
                AuditLog(
                    timestamp = now,
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "ACCESS_EXTENDED_${additionalHours}H",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extend access", e)
            Result.failure(e)
        }
    }

    suspend fun decreaseUserAccess(userId: String, subtractHours: Int = 1, adminId: String, adminEmail: String): Result<Unit> {
        val now = System.currentTimeMillis()
        return try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            val currentExpiry = snapshot.getLong("accessExpiresAt") ?: now
            val baseTime = if (currentExpiry > now) currentExpiry else now
            val newExpiry = (baseTime - (subtractHours * 60 * 60 * 1000L)).coerceAtLeast(now)

            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "accessExpiresAt" to newExpiry
                    )
                ).await()

            recordAuditLog(
                AuditLog(
                    timestamp = now,
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "ACCESS_DECREASED_${subtractHours}H",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrease access", e)
            Result.failure(e)
        }
    }

    suspend fun grantPermanentAccess(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "status" to UserProfile.STATUS_APPROVED,
                        "accessExpiresAt" to 0L
                    )
                ).await()

            recordAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "PERMANENT_ACCESS_GRANTED",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectUser(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        return updateUserStatus(userId, UserProfile.STATUS_REJECTED, "USER_REJECTED", adminId, adminEmail)
    }

    suspend fun suspendUser(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        return updateUserStatus(userId, UserProfile.STATUS_SUSPENDED, "USER_SUSPENDED", adminId, adminEmail)
    }

    suspend fun restoreUser(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        val newExpiry = System.currentTimeMillis() + UserProfile.EIGHT_HOURS_MILLIS
        return try {
            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "status" to UserProfile.STATUS_APPROVED,
                        "accessExpiresAt" to newExpiry
                    )
                ).await()
            recordAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "USER_RESTORED_8H",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun terminateUser(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        return updateUserStatus(userId, UserProfile.STATUS_TERMINATED, "USER_TERMINATED_AND_WIPED", adminId, adminEmail)
    }

    suspend fun deleteUser(userId: String, adminId: String, adminEmail: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId).delete().await()
            recordAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "USER_DELETED_FROM_SYSTEM",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user", e)
            Result.failure(e)
        }
    }

    private suspend fun updateUserStatus(
        userId: String,
        newStatus: String,
        action: String,
        adminId: String,
        adminEmail: String
    ): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("status", newStatus)
                .await()

            recordAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = action,
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user status: $userId to $newStatus", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserComment(userId: String, comment: String, adminId: String, adminEmail: String): Result<Unit> {
        val now = System.currentTimeMillis()
        return try {
            firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "adminComment" to comment.trim(),
                        "adminCommentBy" to adminEmail,
                        "adminCommentAt" to now
                    )
                ).await()

            recordAuditLog(
                AuditLog(
                    timestamp = now,
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "ADMIN_COMMENT_UPDATED",
                    targetUserId = userId,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update admin comment", e)
            Result.failure(e)
        }
    }

    suspend fun setServiceMode(
        mode: String, // "ACTIVE", "MAINTENANCE", "DISABLED"
        adminId: String,
        adminEmail: String,
        customMessage: String? = null
    ): Result<Unit> {
        val (enabled, maintenance) = when (mode.uppercase()) {
            "MAINTENANCE" -> true to true
            "DISABLED" -> false to false
            else -> true to false // ACTIVE
        }

        val defaultMsg = when (mode.uppercase()) {
            "MAINTENANCE" -> "REPLICA is currently under maintenance. Please try again later."
            "DISABLED" -> "REPLICA service is currently unavailable. Please contact an administrator or try again later."
            else -> "REPLICA service is operational."
        }

        return try {
            val data = mapOf(
                "serviceEnabled" to enabled,
                "maintenanceMode" to maintenance,
                "disabledMessage" to (if (!enabled) (customMessage ?: defaultMsg) else "REPLICA service is currently unavailable."),
                "maintenanceMessage" to (if (maintenance) (customMessage ?: defaultMsg) else "REPLICA is currently under maintenance. Please try again later."),
                "updatedBy" to adminEmail,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection("settings").document("service_control")
                .set(data)
                .await()

            recordAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "SERVICE_STATUS_CHANGED_$mode",
                    targetUserId = null,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set service mode", e)
            Result.failure(e)
        }
    }

    suspend fun sendBroadcastMessage(
        title: String,
        message: String,
        adminId: String,
        adminEmail: String
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        return try {
            firestore.collection("settings").document("service_control")
                .update(
                    mapOf(
                        "broadcastTitle" to title.trim(),
                        "broadcastMessage" to message.trim(),
                        "updatedBy" to adminEmail,
                        "updatedAt" to now
                    )
                ).await()

            recordAuditLog(
                AuditLog(
                    timestamp = now,
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "BROADCAST_MESSAGE_SENT",
                    targetUserId = null,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send broadcast message", e)
            Result.failure(e)
        }
    }

    suspend fun clearBroadcastMessage(
        adminId: String,
        adminEmail: String
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        return try {
            firestore.collection("settings").document("service_control")
                .update(
                    mapOf(
                        "broadcastTitle" to null,
                        "broadcastMessage" to null,
                        "updatedBy" to adminEmail,
                        "updatedAt" to now
                    )
                ).await()

            recordAuditLog(
                AuditLog(
                    timestamp = now,
                    adminId = adminId,
                    adminEmail = adminEmail,
                    action = "BROADCAST_MESSAGE_CLEARED",
                    targetUserId = null,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear broadcast message", e)
            Result.failure(e)
        }
    }

    private suspend fun recordAuditLog(log: AuditLog) {
        try {
            val logMap = mapOf(
                "timestamp" to log.timestamp,
                "adminId" to log.adminId,
                "adminEmail" to log.adminEmail,
                "action" to log.action,
                "targetUserId" to log.targetUserId,
                "result" to log.result
            )
            firestore.collection("audit_logs").add(logMap).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record admin audit log", e)
        }
    }
}

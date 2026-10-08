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
                            accessExpiresAt = doc.getLong("accessExpiresAt") ?: 0L
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

    suspend fun setGlobalServiceStatus(
        enabled: Boolean,
        adminId: String,
        adminEmail: String,
        message: String = "REPLICA service is temporarily unavailable."
    ): Result<Unit> {
        return try {
            val data = mapOf(
                "serviceEnabled" to enabled,
                "disabledMessage" to message,
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
                    action = if (enabled) "SERVICE_ENABLED" else "SERVICE_DISABLED",
                    targetUserId = null,
                    result = "SUCCESS"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set global service status", e)
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

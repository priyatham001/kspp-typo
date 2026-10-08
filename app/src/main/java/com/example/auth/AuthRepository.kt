package com.example.auth

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthRepository"

class AuthRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    constructor(context: Context) : this(
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    private val _serviceControl = MutableStateFlow(ServiceControl())
    val serviceControl: StateFlow<ServiceControl> = _serviceControl.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private var serviceListener: ListenerRegistration? = null

    private val _currentUserFlow = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUserFlow: StateFlow<FirebaseUser?> = _currentUserFlow.asStateFlow()

    val currentUser: FirebaseUser? get() = _currentUserFlow.value

    companion object {
        fun isSuperAdminEmail(email: String?): Boolean {
            return email?.equals("nani68629@gmail.com", ignoreCase = true) == true ||
                   email?.equals("pskcoll68629@gmail.com", ignoreCase = true) == true
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentUserFlow.value = user
        if (user != null) {
            listenToProfile(user.uid)
        } else {
            profileListener?.remove()
            profileListener = null
            _currentProfile.value = null
        }
        listenToServiceControl()
    }

    init {
        auth.addAuthStateListener(authStateListener)
        listenToServiceControl()

        auth.currentUser?.let { user ->
            listenToProfile(user.uid)
        }
    }

    suspend fun signInWithGoogleCredential(credential: AuthCredential): Result<UserProfile> {
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw IllegalStateException("No user returned from Google Sign-In")

            val profile = syncUserProfile(firebaseUser)
            listenToProfile(firebaseUser.uid)
            listenToServiceControl()
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Google Sign-In", e)
            Result.failure(e)
        }
    }

    private suspend fun syncUserProfile(user: FirebaseUser): UserProfile {
        val userDocRef = firestore.collection("users").document(user.uid)
        val snapshot = userDocRef.get().await()

        val isOwnerEmail = isSuperAdminEmail(user.email)
        val now = System.currentTimeMillis()

        return if (!snapshot.exists()) {
            // New user registration: gets 8-hour access window upon login
            val defaultStatus = UserProfile.STATUS_APPROVED
            val defaultRole = if (isOwnerEmail) UserProfile.ROLE_SUPER_ADMIN else UserProfile.ROLE_USER
            val defaultExpiresAt = if (isOwnerEmail) 0L else (now + UserProfile.EIGHT_HOURS_MILLIS)

            val newProfile = UserProfile(
                userId = user.uid,
                displayName = user.displayName ?: "User",
                email = user.email ?: "",
                photoUrl = user.photoUrl?.toString(),
                status = defaultStatus,
                role = defaultRole,
                registrationDate = now,
                lastLogin = now,
                accessExpiresAt = defaultExpiresAt
            )

            val dataMap = mapOf(
                "userId" to newProfile.userId,
                "displayName" to newProfile.displayName,
                "email" to newProfile.email,
                "photoUrl" to newProfile.photoUrl,
                "status" to newProfile.status,
                "role" to newProfile.role,
                "registrationDate" to newProfile.registrationDate,
                "lastLogin" to newProfile.lastLogin,
                "accessExpiresAt" to newProfile.accessExpiresAt
            )

            userDocRef.set(dataMap).await()

            recordAuditLog(
                AuditLog(
                    adminId = user.uid,
                    adminEmail = user.email ?: "",
                    action = if (isOwnerEmail) "SUPER_ADMIN_INITIALIZED" else "USER_REGISTERED_8H_ACCESS",
                    targetUserId = user.uid,
                    result = "SUCCESS"
                )
            )

            _currentProfile.value = newProfile
            newProfile
        } else {
            // Existing user
            val existingRole = snapshot.getString("role") ?: UserProfile.ROLE_USER
            val existingStatus = snapshot.getString("status") ?: UserProfile.STATUS_APPROVED
            val existingExpiresAt = snapshot.getLong("accessExpiresAt") ?: if (isOwnerEmail) 0L else (now + UserProfile.EIGHT_HOURS_MILLIS)

            val finalRole = if (isOwnerEmail) UserProfile.ROLE_SUPER_ADMIN else existingRole
            val finalStatus = if (isOwnerEmail) UserProfile.STATUS_APPROVED else existingStatus
            val finalExpiresAt = if (isOwnerEmail) 0L else existingExpiresAt

            val updates = mutableMapOf<String, Any>(
                "lastLogin" to now,
                "displayName" to (user.displayName ?: snapshot.getString("displayName") ?: "User"),
                "accessExpiresAt" to finalExpiresAt
            )
            user.photoUrl?.let { updates["photoUrl"] = it.toString() }
            if (isOwnerEmail) {
                updates["role"] = finalRole
                updates["status"] = finalStatus
            }

            userDocRef.update(updates).await()

            val existingProfile = UserProfile(
                userId = user.uid,
                displayName = user.displayName ?: snapshot.getString("displayName") ?: "User",
                email = user.email ?: snapshot.getString("email") ?: "",
                photoUrl = user.photoUrl?.toString() ?: snapshot.getString("photoUrl"),
                status = finalStatus,
                role = finalRole,
                registrationDate = snapshot.getLong("registrationDate") ?: now,
                lastLogin = now,
                accessExpiresAt = finalExpiresAt
            )
            _currentProfile.value = existingProfile
            existingProfile
        }
    }

    fun listenToProfile(userId: String) {
        profileListener?.remove()
        profileListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Profile snapshot listener: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val profile = UserProfile(
                        userId = snapshot.getString("userId") ?: userId,
                        displayName = snapshot.getString("displayName") ?: "",
                        email = snapshot.getString("email") ?: "",
                        photoUrl = snapshot.getString("photoUrl"),
                        status = snapshot.getString("status") ?: UserProfile.STATUS_PENDING,
                        role = snapshot.getString("role") ?: UserProfile.ROLE_USER,
                        registrationDate = snapshot.getLong("registrationDate") ?: System.currentTimeMillis(),
                        lastLogin = snapshot.getLong("lastLogin") ?: System.currentTimeMillis(),
                        accessExpiresAt = snapshot.getLong("accessExpiresAt") ?: 0L
                    )
                    _currentProfile.value = profile
                }
            }
    }

    private fun listenToServiceControl() {
        serviceListener?.remove()
        try {
            serviceListener = firestore.collection("settings").document("service_control")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "ServiceControl listener: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val enabled = snapshot.getBoolean("serviceEnabled") ?: true
                        val message = snapshot.getString("disabledMessage") ?: "REPLICA service is temporarily unavailable."
                        val updatedBy = snapshot.getString("updatedBy") ?: ""
                        val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()

                        _serviceControl.value = ServiceControl(
                            serviceEnabled = enabled,
                            disabledMessage = message,
                            updatedBy = updatedBy,
                            updatedAt = updatedAt
                        )
                    } else {
                        _serviceControl.value = ServiceControl(serviceEnabled = true)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach ServiceControl listener", e)
        }
    }

    suspend fun recordAuditLog(log: AuditLog) {
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
            Log.w(TAG, "Failed to record audit log: ${e.message}")
        }
    }

    fun signOut(context: Context? = null) {
        profileListener?.remove()
        profileListener = null
        _currentProfile.value = null
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error in auth.signOut", e)
        }
        _currentUserFlow.value = null
        if (context != null) {
            try {
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                    } catch (e: Exception) {
                        Log.w(TAG, "Error clearing credential state: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "CredentialManager error: ${e.message}")
            }
        }
    }

    fun cleanup() {
        auth.removeAuthStateListener(authStateListener)
        profileListener?.remove()
        profileListener = null
        serviceListener?.remove()
        serviceListener = null
    }
}

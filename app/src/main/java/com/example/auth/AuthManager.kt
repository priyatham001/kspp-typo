package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.storage.ScriptDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AuthManager(context: Context) {

    private val db = ScriptDatabase.getDatabase(context)
    private val dao = db.authDao()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("replica_auth_prefs", Context.MODE_PRIVATE)

    val superAdminEmail = "pskcoll68629@gmail.com"

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isGlobalServiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_GLOBAL_SERVICE, true))
    val isGlobalServiceEnabled: StateFlow<Boolean> = _isGlobalServiceEnabled.asStateFlow()

    val allUsers: Flow<List<UserEntity>> = dao.getAllUsers()
    val auditLogs: Flow<List<AuditLogEntity>> = dao.getRecentAuditLogs()

    private var onAccessRevokedCallback: (() -> Unit)? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedSuperAdminAndInitialUsers()
            val savedUserId = prefs.getString(KEY_LOGGED_IN_USER_ID, null)
            if (savedUserId != null) {
                val user = dao.getUserById(savedUserId)
                _currentUser.value = user
            } else {
                // Auto-sign in as Super Admin by default on initial launch so the owner can access all features immediately
                val admin = dao.getUserByEmail(superAdminEmail)
                if (admin != null) {
                    _currentUser.value = admin
                    prefs.edit().putString(KEY_LOGGED_IN_USER_ID, admin.id).apply()
                }
            }

            // Real-time synchronization loop: re-check user status from database
            dao.getAllUsers().collect { userList ->
                val curr = _currentUser.value ?: return@collect
                val updated = userList.find { it.id == curr.id }
                if (updated != null) {
                    _currentUser.value = updated
                    if (updated.status == AccountStatus.SUSPENDED.name ||
                        updated.status == AccountStatus.TERMINATED.name ||
                        !_isGlobalServiceEnabled.value
                    ) {
                        onAccessRevokedCallback?.invoke()
                    }
                }
            }
        }
    }

    fun setOnAccessRevokedCallback(callback: () -> Unit) {
        onAccessRevokedCallback = callback
    }

    suspend fun signInWithGoogle(
        displayName: String,
        email: String,
        photoUrl: String? = null
    ): UserEntity {
        var user = dao.getUserByEmail(email)
        val isSuperAdmin = email.equals(superAdminEmail, ignoreCase = true)

        if (user == null) {
            val role = if (isSuperAdmin) UserRole.SUPER_ADMIN.name else UserRole.USER.name
            val status = if (isSuperAdmin) AccountStatus.APPROVED.name else AccountStatus.PENDING.name
            user = UserEntity(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
                role = role,
                status = status,
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
            dao.insertUser(user)
        } else {
            user = user.copy(
                displayName = displayName,
                photoUrl = photoUrl ?: user.photoUrl,
                lastActiveAt = System.currentTimeMillis()
            )
            dao.updateUser(user)
        }

        _currentUser.value = user
        prefs.edit().putString(KEY_LOGGED_IN_USER_ID, user.id).apply()
        return user
    }

    fun signOut() {
        _currentUser.value = null
        prefs.edit().remove(KEY_LOGGED_IN_USER_ID).apply()
    }

    suspend fun approveUser(actor: UserEntity, targetId: String): Boolean {
        if (!hasAdminPrivileges(actor)) return false
        val target = dao.getUserById(targetId) ?: return false
        dao.updateUserStatus(targetId, AccountStatus.APPROVED.name)
        logAdminAction(actor.email, "APPROVED_USER", target.email, "Approved user account access")
        return true
    }

    suspend fun rejectUser(actor: UserEntity, targetId: String): Boolean {
        if (!hasAdminPrivileges(actor)) return false
        val target = dao.getUserById(targetId) ?: return false
        dao.updateUserStatus(targetId, AccountStatus.TERMINATED.name)
        logAdminAction(actor.email, "REJECTED_USER", target.email, "Rejected pending registration")
        checkRevokeIfCurrent(targetId)
        return true
    }

    suspend fun suspendUser(actor: UserEntity, targetId: String): Boolean {
        if (!hasAdminPrivileges(actor)) return false
        val target = dao.getUserById(targetId) ?: return false
        dao.updateUserStatus(targetId, AccountStatus.SUSPENDED.name)
        logAdminAction(actor.email, "SUSPENDED_USER", target.email, "Suspended active access")
        checkRevokeIfCurrent(targetId)
        return true
    }

    suspend fun terminateUser(actor: UserEntity, targetId: String): Boolean {
        if (!hasAdminPrivileges(actor)) return false
        val target = dao.getUserById(targetId) ?: return false
        dao.updateUserStatus(targetId, AccountStatus.TERMINATED.name)
        logAdminAction(actor.email, "TERMINATED_USER", target.email, "Permanently terminated service access")
        checkRevokeIfCurrent(targetId)
        return true
    }

    suspend fun restoreUser(actor: UserEntity, targetId: String): Boolean {
        if (!hasAdminPrivileges(actor)) return false
        val target = dao.getUserById(targetId) ?: return false
        dao.updateUserStatus(targetId, AccountStatus.APPROVED.name)
        logAdminAction(actor.email, "RESTORED_USER", target.email, "Restored access to approved state")
        return true
    }

    suspend fun toggleGlobalKillSwitch(actor: UserEntity, enable: Boolean): Boolean {
        if (actor.role != UserRole.SUPER_ADMIN.name) return false
        _isGlobalServiceEnabled.value = enable
        prefs.edit().putBoolean(KEY_GLOBAL_SERVICE, enable).apply()
        logAdminAction(
            actor.email,
            if (enable) "ENABLED_GLOBAL_SERVICE" else "DISABLED_GLOBAL_SERVICE",
            "ALL_USERS",
            "Super Admin modified service availability"
        )
        if (!enable) {
            onAccessRevokedCallback?.invoke()
        }
        return true
    }

    private fun checkRevokeIfCurrent(targetId: String) {
        if (_currentUser.value?.id == targetId) {
            onAccessRevokedCallback?.invoke()
        }
    }

    fun hasAdminPrivileges(user: UserEntity?): Boolean {
        if (user == null) return false
        return user.role == UserRole.ADMIN.name || user.role == UserRole.SUPER_ADMIN.name
    }

    private suspend fun logAdminAction(
        adminEmail: String,
        action: String,
        targetEmail: String,
        details: String
    ) {
        dao.insertAuditLog(
            AuditLogEntity(
                adminEmail = adminEmail,
                action = action,
                targetEmail = targetEmail,
                details = details,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun seedSuperAdminAndInitialUsers() {
        if (dao.getUserByEmail(superAdminEmail) == null) {
            // Seed Super Admin (Service Owner)
            dao.insertUser(
                UserEntity(
                    id = "super_admin_owner",
                    displayName = "Service Owner",
                    email = superAdminEmail,
                    role = UserRole.SUPER_ADMIN.name,
                    status = AccountStatus.APPROVED.name,
                    createdAt = System.currentTimeMillis()
                )
            )

            // Seed sample pending and approved users for testing admin controls
            dao.insertUser(
                UserEntity(
                    id = "demo_pending_user",
                    displayName = "Demo Student",
                    email = "student@example.com",
                    role = UserRole.USER.name,
                    status = AccountStatus.PENDING.name,
                    createdAt = System.currentTimeMillis() - 86400000
                )
            )

            dao.insertUser(
                UserEntity(
                    id = "demo_approved_user",
                    displayName = "Priyatham",
                    email = "priyatham@example.com",
                    role = UserRole.USER.name,
                    status = AccountStatus.APPROVED.name,
                    createdAt = System.currentTimeMillis() - 172800000
                )
            )

            logAdminAction(
                adminEmail = superAdminEmail,
                action = "SYSTEM_INITIALIZED",
                targetEmail = "ALL",
                details = "REPLICA access management system initialized"
            )
        }
    }

    companion object {
        private const val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
        private const val KEY_GLOBAL_SERVICE = "is_global_service_enabled"
    }
}

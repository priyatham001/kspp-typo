package com.example.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AdminRepository
import com.example.auth.AuditLog
import com.example.auth.AuthRepository
import com.example.auth.GoogleSignInHelper
import com.example.auth.ServiceControl
import com.example.auth.UserProfile
import com.example.bluetooth.BluetoothHidManager
import com.example.bluetooth.HidConnectionState
import com.example.keyboard.KeyboardMapper
import com.example.service.TypingForegroundService
import com.example.storage.AppThemeMode
import com.example.storage.PasscodeManager
import com.example.storage.ScriptEntity
import com.example.storage.ScriptManager
import com.example.storage.SettingsManager
import com.example.typing.TypingEngine
import com.example.typing.TypingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val authRepository = AuthRepository(application.applicationContext)
    val adminRepository = AdminRepository(application.applicationContext)
    val hidManager = BluetoothHidManager(application.applicationContext)
    val typingEngine = TypingEngine(hidManager, viewModelScope)
    val scriptManager = ScriptManager(application.applicationContext)
    val settingsManager = SettingsManager(application.applicationContext)
    val passcodeManager = PasscodeManager(application.applicationContext)

    val userProfile: StateFlow<UserProfile?> = authRepository.currentProfile
    val serviceControl: StateFlow<ServiceControl> = authRepository.serviceControl

    val connectionState: StateFlow<HidConnectionState> = hidManager.connectionState
    val pairedDevices: StateFlow<List<BluetoothDevice>> = hidManager.pairedDevices
    val connectedDevice: StateFlow<BluetoothDevice?> = hidManager.connectedDevice

    val typingState: StateFlow<TypingState> = typingEngine.typingState
    val typingDelayMs: StateFlow<Long> = typingEngine.currentDelayMs

    val allScripts = scriptManager.scripts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val adminAllUsers = adminRepository.getAllUsersFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val adminAuditLogs = adminRepository.getAuditLogsFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val keepScreenAwake: StateFlow<Boolean> = settingsManager.keepScreenAwake
    val themeMode: StateFlow<AppThemeMode> = settingsManager.themeMode
    val isUnlocked: StateFlow<Boolean> = passcodeManager.isUnlocked

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val defaultSampleText = """REPLICA - I replicate keyboard
Hello World!

This is a test message from REPLICA.
Website: https://example.com
Command: git status

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello CodeTantra & Windows!");
    }
}"""

    private val _editorText = MutableStateFlow(defaultSampleText)
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _editorTitle = MutableStateFlow("greeting.txt")
    val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

    private val _currentScriptId = MutableStateFlow<Long?>(null)
    val currentScriptId: StateFlow<Long?> = _currentScriptId.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isTestingAllKeys = MutableStateFlow(false)
    val isTestingAllKeys: StateFlow<Boolean> = _isTestingAllKeys.asStateFlow()

    init {
        typingEngine.setDelay(settingsManager.defaultDelayMs.value)
        hidManager.refresh()

        // 1. Monitor real-time status revocation & 8-hour expiration during active sessions
        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null) {
                    if (profile.status == UserProfile.STATUS_TERMINATED) {
                        // Terminated: stop typing, disconnect, and wipe all local stored scripts
                        if (typingEngine.typingState.value is TypingState.Typing ||
                            typingEngine.typingState.value is TypingState.Paused) {
                            typingEngine.stopTyping()
                        }
                        hidManager.disconnectDevice()
                        scriptManager.clearAll()
                        _editorText.value = ""
                        _currentScriptId.value = null
                        _userMessage.value = "Access permanently terminated by administrator. Local data wiped."
                    } else if (profile.isAccessExpired) {
                        // 8-hour access expired
                        if (typingEngine.typingState.value is TypingState.Typing ||
                            typingEngine.typingState.value is TypingState.Paused) {
                            typingEngine.stopTyping()
                        }
                        hidManager.disconnectDevice()
                        _userMessage.value = "8-hour access period expired. Contact admin nani68629@gmail.com to continue."
                    } else if (!profile.isApproved) {
                        if (typingEngine.typingState.value is TypingState.Typing ||
                            typingEngine.typingState.value is TypingState.Paused) {
                            typingEngine.stopTyping()
                        }
                        hidManager.disconnectDevice()
                        _userMessage.value = "Access revoked: ${profile.status}. Auto-typing stopped."
                    }
                }
            }
        }

        // 2. Monitor real-time global service status
        viewModelScope.launch {
            serviceControl.collect { sc ->
                if (!sc.isOperational) {
                    val profile = userProfile.value
                    if (profile?.isSuperAdmin != true) {
                        if (typingEngine.typingState.value is TypingState.Typing ||
                            typingEngine.typingState.value is TypingState.Paused) {
                            typingEngine.stopTyping()
                            _userMessage.value = if (sc.maintenanceMode) sc.maintenanceMessage else sc.disabledMessage
                        }
                    }
                }
            }
        }

        // 3. Update foreground service notification
        viewModelScope.launch {
            typingEngine.typingState.collect { state ->
                when (state) {
                    is TypingState.Typing -> {
                        val text = "REPLICA: ${state.currentIndex} / ${state.totalChars} (${String.format("%.1f", state.percent)}%)"
                        TypingForegroundService.start(getApplication(), text)
                    }
                    is TypingState.Paused -> {
                        val text = "Paused: ${state.currentIndex} / ${state.totalChars}"
                        TypingForegroundService.start(getApplication(), text)
                    }
                    is TypingState.Completed -> {
                        TypingForegroundService.stop(getApplication())
                        _userMessage.value = "Typing completed successfully! (${state.totalChars} characters sent)"
                    }
                    is TypingState.Stopped -> {
                        TypingForegroundService.stop(getApplication())
                    }
                    is TypingState.Error -> {
                        TypingForegroundService.stop(getApplication())
                        _userMessage.value = state.message
                    }
                    TypingState.Idle -> {
                        TypingForegroundService.stop(getApplication())
                    }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            try {
                val credential = GoogleSignInHelper.getGoogleAuthCredential(context)
                if (credential != null) {
                    val result = authRepository.signInWithGoogleCredential(credential)
                    result.onSuccess { profile ->
                        _userMessage.value = "Signed in as ${profile.displayName}"
                    }.onFailure { error ->
                        _userMessage.value = "Sign-in failed: ${error.localizedMessage ?: "Unknown error"}"
                    }
                } else {
                    _userMessage.value = "Google sign-in was cancelled."
                }
            } catch (e: Exception) {
                _userMessage.value = "Authentication error: ${e.localizedMessage}"
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun signOut(context: Context? = null) {
        try {
            typingEngine.stopTyping()
        } catch (_: Exception) {}

        // Clear user session temporary state cleanly
        _userMessage.value = null
        _currentScriptId.value = null
        _editorText.value = defaultSampleText
        _editorTitle.value = "greeting.txt"
        _isSigningIn.value = false

        authRepository.signOut(context)
    }

    fun refreshAuthStatus() {
        authRepository.currentUser?.let {
            authRepository.listenToProfile(it.uid)
        }
    }

    fun updateEditorText(newText: String) {
        _editorText.value = newText
    }

    fun setTypingDelay(delayMs: Long) {
        typingEngine.setDelay(delayMs)
        settingsManager.setDefaultDelayMs(delayMs)
    }

    fun startTyping() {
        // Enforce Server-Side Authorization:
        val profile = userProfile.value
        if (profile == null || !profile.hasActiveAccess) {
            val msg = if (profile?.isAccessExpired == true) {
                "8-hour access expired. Waiting for admin nani68629@gmail.com to continue access."
            } else {
                "Access not approved. Status: ${profile?.status ?: "Unauthenticated"}"
            }
            _userMessage.value = msg
            return
        }

        val sc = serviceControl.value
        if (!sc.isOperational && !profile.isSuperAdmin) {
            _userMessage.value = if (sc.maintenanceMode) sc.maintenanceMessage else sc.disabledMessage
            return
        }

        if (!isUnlocked.value) {
            _userMessage.value = "Owner passcode authentication required."
            return
        }

        val text = _editorText.value
        if (text.isBlank()) {
            _userMessage.value = "There is no text to type. Please enter text first."
            return
        }
        val conn = connectionState.value
        if (conn !is HidConnectionState.Connected) {
            _userMessage.value = "No HID keyboard connection. Connect to Windows laptop first."
            return
        }
        typingEngine.startTyping(text)
    }

    fun pauseTyping() {
        typingEngine.pauseTyping()
    }

    fun resumeTyping() {
        val profile = userProfile.value
        if (profile == null || !profile.hasActiveAccess) {
            _userMessage.value = "Access not approved or 8-hour period expired."
            return
        }
        typingEngine.resumeTyping()
    }

    fun stopTyping() {
        typingEngine.stopTyping()
    }

    fun clearEditor() {
        typingEngine.stopTyping()
        _editorText.value = ""
        _editorTitle.value = "Untitled.txt"
        _currentScriptId.value = null
    }

    fun loadSampleText() {
        typingEngine.stopTyping()
        _editorText.value = defaultSampleText
        _editorTitle.value = "greeting.txt"
        _currentScriptId.value = null
    }

    fun saveCurrentScript(title: String) {
        val finalTitle = title.ifBlank { "Text_${System.currentTimeMillis() / 1000}.txt" }
        viewModelScope.launch {
            val content = _editorText.value
            val id = _currentScriptId.value
            if (id != null) {
                scriptManager.updateScript(id, finalTitle, content)
            } else {
                val newId = scriptManager.saveScript(finalTitle, content)
                _currentScriptId.value = newId
            }
            _editorTitle.value = finalTitle
            _userMessage.value = "Saved '$finalTitle' locally."
        }
    }

    fun loadScript(script: ScriptEntity) {
        typingEngine.stopTyping()
        _editorText.value = script.content
        _editorTitle.value = script.title
        _currentScriptId.value = script.id
        _userMessage.value = "Loaded '${script.title}' into editor."
    }

    fun deleteScript(id: Long) {
        viewModelScope.launch {
            scriptManager.deleteScript(id)
            if (_currentScriptId.value == id) {
                _currentScriptId.value = null
            }
            _userMessage.value = "Document deleted."
        }
    }

    fun renameScript(id: Long, newTitle: String) {
        viewModelScope.launch {
            scriptManager.renameScript(id, newTitle)
            if (_currentScriptId.value == id) {
                _editorTitle.value = newTitle
            }
        }
    }

    fun connectDevice(device: BluetoothDevice) {
        val profile = userProfile.value
        if (profile == null || !profile.isApproved) {
            _userMessage.value = "Approved account required for Bluetooth operations."
            return
        }
        hidManager.connectDevice(device)
    }

    fun disconnectDevice() {
        typingEngine.stopTyping()
        hidManager.disconnectDevice()
    }

    fun refreshBluetooth() {
        hidManager.refresh()
    }

    fun sendTestKey(keyCode: Byte, modifier: Byte = 0) {
        val profile = userProfile.value
        if (profile == null || !profile.isApproved) {
            _userMessage.value = "Approved account required."
            return
        }
        typingEngine.sendDirectKey(keyCode, modifier)
    }

    fun runTestAllKeys() {
        val profile = userProfile.value
        if (profile == null || !profile.isApproved) {
            _userMessage.value = "Approved account required."
            return
        }
        val conn = connectionState.value
        if (conn !is HidConnectionState.Connected) {
            _userMessage.value = "Connect to Windows laptop via Bluetooth HID first!"
            return
        }
        _isTestingAllKeys.value = true
        typingEngine.typeDirectString(KeyboardMapper.ALL_KEYS_TEST_STRING) {
            _isTestingAllKeys.value = false
            _userMessage.value = "Test string completed on host!"
        }
    }

    // Admin Operations
    suspend fun adminApproveUser(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.approveUser(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "User approved." else "Failed to approve user."
    }

    suspend fun adminRejectUser(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.rejectUser(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "User rejected." else "Failed to reject user."
    }

    suspend fun adminSuspendUser(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.suspendUser(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "User suspended." else "Failed to suspend user."
    }

    suspend fun adminRestoreUser(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.restoreUser(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "User restored to Approved." else "Failed to restore user."
    }

    suspend fun adminTerminateUser(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.terminateUser(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "User access terminated." else "Failed to terminate user."
    }

    suspend fun adminToggleService(enabled: Boolean, adminId: String, adminEmail: String) {
        val res = adminRepository.setGlobalServiceStatus(enabled, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) {
            if (enabled) "REPLICA service enabled." else "REPLICA service disabled."
        } else {
            "Failed to change service status."
        }
    }

    suspend fun adminSetServiceMode(mode: String, adminId: String, adminEmail: String, customMsg: String? = null) {
        val res = adminRepository.setServiceMode(mode, adminId, adminEmail, customMsg)
        _userMessage.value = if (res.isSuccess) "Service status changed to $mode." else "Failed to update service mode."
    }

    suspend fun adminUpdateUserComment(userId: String, comment: String, adminId: String, adminEmail: String) {
        val res = adminRepository.updateUserComment(userId, comment, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "Admin comment saved." else "Failed to save admin comment."
    }

    suspend fun adminExtendAccess(userId: String, hours: Int = 8, adminId: String, adminEmail: String) {
        val res = adminRepository.extendUserAccess(userId, hours, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "Access extended by $hours hours." else "Failed to extend access."
    }

    suspend fun adminGrantPermanentAccess(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.grantPermanentAccess(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "Permanent access granted." else "Failed to grant permanent access."
    }

    suspend fun adminDeleteUser(userId: String, adminId: String, adminEmail: String) {
        val res = adminRepository.deleteUser(userId, adminId, adminEmail)
        _userMessage.value = if (res.isSuccess) "User permanently deleted from REPLICA." else "Failed to delete user."
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        settingsManager.setKeepScreenAwake(enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsManager.setThemeMode(mode)
    }

    fun dismissMessage() {
        _userMessage.value = null
    }

    fun lockNow() {
        typingEngine.stopTyping()
        passcodeManager.lock()
        _userMessage.value = "Locked! Keystroke transmission stopped."
    }

    fun setupPasscode(passcode: String): Boolean {
        return passcodeManager.setupPasscode(passcode)
    }

    fun verifyPasscode(passcode: String): Boolean {
        val ok = passcodeManager.verifyPasscode(passcode)
        if (!ok) {
            _userMessage.value = "Incorrect passcode. Access denied."
        }
        return ok
    }

    override fun onCleared() {
        super.onCleared()
        typingEngine.stopTyping()
        hidManager.cleanup()
        authRepository.cleanup()
    }
}

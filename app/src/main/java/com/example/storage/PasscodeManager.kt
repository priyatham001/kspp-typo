package com.example.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

class PasscodeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pskbtauto_auth", Context.MODE_PRIVATE)

    private val _isUnlocked = MutableStateFlow(!isPasscodeSet())
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun isPasscodeSet(): Boolean {
        return prefs.contains(KEY_PASSCODE_HASH)
    }

    fun setupPasscode(passcode: String): Boolean {
        if (passcode.length < 4) return false
        val salt = generateSalt()
        val hash = hashPasscode(passcode, salt)
        prefs.edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_PASSCODE_HASH, hash)
            .apply()
        _isUnlocked.value = true
        return true
    }

    fun verifyPasscode(enteredPasscode: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expectedHash = prefs.getString(KEY_PASSCODE_HASH, null) ?: return false
        val computedHash = hashPasscode(enteredPasscode, salt)
        val valid = computedHash == expectedHash
        if (valid) {
            _isUnlocked.value = true
        }
        return valid
    }

    fun lock() {
        if (isPasscodeSet()) {
            _isUnlocked.value = false
        }
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPasscode(passcode: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = (passcode + salt).toByteArray(Charsets.UTF_8)
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_PASSCODE_HASH = "owner_passcode_hash"
        private const val KEY_SALT = "owner_salt"
    }
}

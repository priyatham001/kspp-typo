package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AuthRepository
import com.example.auth.ServiceControl
import com.example.auth.UserProfile
import com.example.keyboard.KeyboardMapper
import com.example.storage.PasscodeManager
import com.example.storage.ScriptDatabase
import com.example.storage.ScriptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: ScriptDatabase
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScriptDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun readStringFromContext() {
        val appName = context.getString(R.string.app_name)
        assertEquals("replica_kspp", appName)
    }

    @Test
    fun testRoomDatabase_insertAndClearAll() = runBlocking {
        val dao = db.scriptDao()
        val textDoc1 = ScriptEntity(title = "test1.txt", content = "doc 1", language = "text")
        val textDoc2 = ScriptEntity(title = "test2.txt", content = "doc 2", language = "text")
        dao.insertScript(textDoc1)
        dao.insertScript(textDoc2)
        assertEquals(2, dao.getCount())

        // Remote wipe on termination
        dao.clearAll()
        assertEquals(0, dao.getCount())
    }

    @Test
    fun testOwnerPasscode_setupAndVerification() {
        val passcodeManager = PasscodeManager(context)
        
        val setupSuccess = passcodeManager.setupPasscode("1234")
        assertTrue(setupSuccess)
        assertTrue(passcodeManager.isPasscodeSet())
        assertTrue(passcodeManager.isUnlocked.value)

        passcodeManager.lock()
        assertFalse(passcodeManager.isUnlocked.value)

        val wrongAttempt = passcodeManager.verifyPasscode("9999")
        assertFalse(wrongAttempt)
        assertFalse(passcodeManager.isUnlocked.value)

        val correctAttempt = passcodeManager.verifyPasscode("1234")
        assertTrue(correctAttempt)
        assertTrue(passcodeManager.isUnlocked.value)
    }

    @Test
    fun testSuperAdminEmails() {
        assertTrue(AuthRepository.isSuperAdminEmail("nani68629@gmail.com"))
        assertTrue(AuthRepository.isSuperAdminEmail("pskcoll68629@gmail.com"))
        assertFalse(AuthRepository.isSuperAdminEmail("regularuser@example.com"))
    }

    @Test
    fun testUserProfile_eightHourAccessLogic() {
        val now = System.currentTimeMillis()

        // 1. User with 8 hours of active access
        val activeUser = UserProfile(
            userId = "user_8h",
            displayName = "Active User",
            email = "user8h@test.com",
            status = UserProfile.STATUS_APPROVED,
            role = UserProfile.ROLE_USER,
            accessExpiresAt = now + (8 * 60 * 60 * 1000L)
        )
        assertTrue(activeUser.isApproved)
        assertFalse(activeUser.isAccessExpired)
        assertTrue(activeUser.hasActiveAccess)
        assertTrue(activeUser.getRemainingTimeFormatted().contains("h"))

        // 2. User whose 8 hours have expired
        val expiredUser = UserProfile(
            userId = "user_expired",
            displayName = "Expired User",
            email = "expired@test.com",
            status = UserProfile.STATUS_APPROVED,
            role = UserProfile.ROLE_USER,
            accessExpiresAt = now - 1000L // 1 second in the past
        )
        assertTrue(expiredUser.isApproved)
        assertTrue(expiredUser.isAccessExpired)
        assertFalse(expiredUser.hasActiveAccess)
        assertEquals("Expired", expiredUser.getRemainingTimeFormatted())

        // 3. Super Admin nani68629@gmail.com has permanent access
        val adminUser = UserProfile(
            userId = "admin_nani",
            displayName = "Nani Admin",
            email = "nani68629@gmail.com",
            status = UserProfile.STATUS_APPROVED,
            role = UserProfile.ROLE_SUPER_ADMIN,
            accessExpiresAt = 0L
        )
        assertTrue(adminUser.isAdmin)
        assertTrue(adminUser.isSuperAdmin)
        assertFalse(adminUser.isAccessExpired)
        assertTrue(adminUser.hasActiveAccess)
        assertEquals("Permanent (Admin)", adminUser.getRemainingTimeFormatted())
    }

    @Test
    fun testServiceControl_globalStatus() {
        val enabledControl = ServiceControl(serviceEnabled = true)
        assertTrue(enabledControl.serviceEnabled)

        val disabledControl = ServiceControl(
            serviceEnabled = false,
            disabledMessage = "REPLICA service is temporarily unavailable."
        )
        assertFalse(disabledControl.serviceEnabled)
        assertEquals("REPLICA service is temporarily unavailable.", disabledControl.disabledMessage)
    }

    @Test
    fun testGenericTextKeyboardMapping_allCharacters() {
        val sampleText = """Hello World! 123
Website: https://example.com/test?id=10
Path: C:\Users\User\Documents\File.txt
JSON: {"key": "value", "count": 100}
Math: a + b = c, x * y - z / 2 < 10 && total > 5"""

        for (char in sampleText) {
            if (char == '\r') continue
            val stroke = KeyboardMapper.mapChar(char)
            assertNotNull("Character '$char' should be supported on US QWERTY", stroke)
        }
    }

    @Test
    fun testUsbTypingManager_neverFakesSyncAndCompletesRealHandshake() = runBlocking {
        val usbManager = com.example.usb.UsbTypingManager(context)

        // 1. Initially must be Disconnected and NEVER Synced without real socket handshake
        val initialState = usbManager.connectionState.value
        assertTrue(initialState is com.example.usb.UsbConnectionState.Disconnected)
        assertFalse(usbManager.diagnosticsReport.value.handshakeCompleted)
        assertFalse(usbManager.diagnosticsReport.value.isReadyToType)

        // 2. Start a real local mock companion TCP server on an ephemeral port to verify protocol v1
        val mockCompanionServer = java.net.ServerSocket(0)
        val port = mockCompanionServer.localPort

        val receivedCommands = java.util.Collections.synchronizedList(mutableListOf<String>())
        val serverThread = Thread {
            try {
                val client = mockCompanionServer.accept()
                val reader = java.io.BufferedReader(java.io.InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8))

                while (!client.isClosed) {
                    val line = reader.readLine() ?: break
                    receivedCommands.add(line)
                    val json = org.json.JSONObject(line)
                    when (json.optString("type")) {
                        "HANDSHAKE_SYN" -> {
                            val ack = org.json.JSONObject().apply {
                                put("type", "HANDSHAKE_ACK")
                                put("protocolVersion", 1)
                                put("server", "replica_windows_companion")
                                put("companionVersion", "1.1.0")
                                put("os", "Windows")
                                put("status", "SYNCED")
                                put("adbPath", "C:\\platform-tools\\adb.exe")
                                put("adbVersion", "Android Debug Bridge version 1.0.41")
                                put("adbDevices", "R58M123456 (device)")
                                put("deviceAuthorized", true)
                                put("reverseStatus", "OK (tcp:8989 -> tcp:8989)")
                                put("forwardStatus", "OK (tcp:8990 -> tcp:8990)")
                                put("tunnelMode", "Reverse Tunnel (8989)")
                            }.toString()
                            writer.write(ack)
                            writer.newLine()
                            writer.flush()
                        }
                        "PING" -> {
                            writer.write("{\"type\":\"PONG\",\"status\":\"OK\"}")
                            writer.newLine()
                            writer.flush()
                        }
                        "CMD_START" -> {
                            val text = json.optString("text", "")
                            writer.write("{\"type\":\"PROGRESS\",\"currentIndex\":${text.length},\"totalChars\":${text.length},\"percent\":1.0}")
                            writer.newLine()
                            writer.flush()
                            writer.write("{\"type\":\"DONE\",\"totalTyped\":${text.length}}")
                            writer.newLine()
                            writer.flush()
                        }
                        "CMD_PAUSE" -> {
                            writer.write("{\"type\":\"PAUSED\"}")
                            writer.newLine()
                            writer.flush()
                        }
                        "CMD_RESUME" -> {
                            writer.write("{\"type\":\"RESUMED\"}")
                            writer.newLine()
                            writer.flush()
                        }
                        "CMD_STOP" -> {
                            writer.write("{\"type\":\"STOPPED\"}")
                            writer.newLine()
                            writer.flush()
                        }
                    }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }

        // 3. Connect & verify real HANDSHAKE_SYN -> HANDSHAKE_ACK transition to Synced
        val syncedState = usbManager.checkConnection("127.0.0.1", port)
        assertTrue("Expected Synced state after real handshake", syncedState is com.example.usb.UsbConnectionState.Synced)
        val synced = syncedState as com.example.usb.UsbConnectionState.Synced
        assertEquals("1.1.0", synced.companionVersion)
        assertEquals(1, synced.protocolVersion)

        // Verify diagnostics report populated from companion telemetry
        val report = usbManager.diagnosticsReport.value
        assertTrue(report.handshakeCompleted)
        assertTrue(report.heartbeatVerified)
        assertTrue(report.isReadyToType)
        assertEquals("R58M123456 (device)", report.companionAdbDevices)

        // 4. Test PING / PONG heartbeat on existing socket
        val pingState = usbManager.checkConnection("127.0.0.1", port)
        assertTrue(pingState is com.example.usb.UsbConnectionState.Synced)

        // 5. Test Start, Pause, Resume, Stop, and Unicode text
        val unicodeSample = "Hello Notepad! Unicode: αβγ 🚀 Line1\nLine2"
        assertTrue(usbManager.startTyping(unicodeSample, 10))
        assertTrue(usbManager.pauseTyping())
        assertTrue(usbManager.resumeTyping())
        assertTrue(usbManager.stopTyping())

        kotlinx.coroutines.delay(150)
        assertTrue(receivedCommands.any { it.contains("HANDSHAKE_SYN") })
        assertTrue(receivedCommands.any { it.contains("CMD_START") && it.contains("Hello Notepad!") })
        assertTrue(receivedCommands.any { it.contains("CMD_PAUSE") })
        assertTrue(receivedCommands.any { it.contains("CMD_RESUME") })
        assertTrue(receivedCommands.any { it.contains("CMD_STOP") })

        mockCompanionServer.close()
        usbManager.cleanup()
    }

    @Test
    fun testMainViewModel_initializesWithoutForceCloseOnStartup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.MainViewModel(app)
        assertNotNull(vm.userProfile.value)
        assertNotNull(vm.usbConnectionState.value)
        assertNotNull(vm.usbDiagnosticsReport.value)
        vm.usbTypingManager.cleanup()
        vm.hidManager.cleanup()
        vm.authRepository.cleanup()
    }
}

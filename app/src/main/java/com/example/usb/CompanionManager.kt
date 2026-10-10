package com.example.usb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val TAG = "CompanionManager"

class CompanionManager(private val context: Context) {

    private val firestore by lazy {
        try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        } catch (e: Exception) {
            FirebaseFirestore.getInstance()
        }
    }

    private val companionDir = File(context.filesDir, "companion").apply { mkdirs() }
    val localCompanionFile = File(companionDir, "replica-companion.exe")

    init {
        ensureLocalCompanionExtracted()
    }

    /**
     * Extracts bundled replica-companion.exe from assets to internal storage if needed.
     */
    fun ensureLocalCompanionExtracted(): Boolean {
        if (localCompanionFile.exists() && localCompanionFile.length() > 0) {
            return true
        }

        return try {
            context.assets.open("companion/replica-companion.exe").use { input ->
                FileOutputStream(localCompanionFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Bundled replica-companion.exe extracted to ${localCompanionFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract companion from assets: ${e.message}")
            false
        }
    }

    /**
     * Compute SHA256 of the given file.
     */
    fun calculateFileSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Real-time Flow of Companion metadata from Firestore.
     */
    fun getCompanionInfoFlow(): Flow<CompanionInfo> = callbackFlow {
        val docRef = firestore.collection("settings").document("windows_companion")
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Firestore error reading companion config: ${error.message}")
                trySend(getDefaultCompanionInfo())
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val info = CompanionInfo(
                    version = snapshot.getString("version") ?: "1.0.1",
                    protocolVersion = snapshot.getLong("protocolVersion")?.toInt() ?: 1,
                    minAppVersion = snapshot.getString("minAppVersion") ?: "1.0.0",
                    fileName = snapshot.getString("fileName") ?: "replica-companion.exe",
                    fileSize = snapshot.getLong("fileSize") ?: localCompanionFile.length(),
                    sha256 = snapshot.getString("sha256") ?: calculateFileSha256(localCompanionFile),
                    downloadUrl = snapshot.getString("downloadUrl") ?: "/download/replica-companion.exe",
                    uploadedBy = snapshot.getString("uploadedBy") ?: "admin",
                    lastUpdated = snapshot.getLong("lastUpdated") ?: System.currentTimeMillis(),
                    releaseNotes = snapshot.getString("releaseNotes") ?: "Official Windows companion program"
                )
                trySend(info)
            } else {
                trySend(getDefaultCompanionInfo())
            }
        }

        awaitClose { listener.remove() }
    }

    fun getDefaultCompanionInfo(): CompanionInfo {
        val size = if (localCompanionFile.exists()) localCompanionFile.length() else 47104L
        val sha = if (localCompanionFile.exists()) calculateFileSha256(localCompanionFile) else "9ccd84e9ce31e9ecc750047b1b944dd266ae9dcc1e83928a74d5301f82990bd8"
        return CompanionInfo(
            version = "1.0.1",
            protocolVersion = 1,
            minAppVersion = "1.0.0",
            fileName = "replica-companion.exe",
            fileSize = size,
            sha256 = sha,
            downloadUrl = "/download/replica-companion.exe",
            uploadedBy = "admin",
            lastUpdated = System.currentTimeMillis(),
            releaseNotes = "Official Windows companion program for USB auto-typing into Windows applications."
        )
    }

    /**
     * Share the physical replica-companion.exe file via Android Share sheet.
     */
    fun shareCompanionFile(activityContext: Context) {
        ensureLocalCompanionExtracted()
        if (!localCompanionFile.exists() || localCompanionFile.length() == 0L) {
            Log.e(TAG, "Companion file does not exist to share")
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                activityContext,
                "${activityContext.packageName}.fileprovider",
                localCompanionFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "REPLICA Windows Companion (.exe)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Here is the REPLICA Windows Companion program (replica-companion.exe) for USB auto-typing into your PC!"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activityContext.startActivity(Intent.createChooser(shareIntent, "Share Windows Companion (.exe)"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share companion file: ${e.message}", e)
        }
    }

    /**
     * Share the direct download link or instructions.
     */
    fun shareCompanionDownloadLink(activityContext: Context, serverBaseUrl: String) {
        val cleanUrl = if (serverBaseUrl.endsWith("/")) serverBaseUrl.dropLast(1) else serverBaseUrl
        val downloadLink = "$cleanUrl/download/replica-companion.exe"
        val shareText = """
            💻 REPLICA - Windows Companion v1.0.1
            Download the official companion program for USB auto-typing:
            $downloadLink

            Instructions:
            1. Download and run replica-companion.exe on your Windows PC.
            2. Connect your phone using a USB cable with USB Debugging enabled.
            3. Open REPLICA on your phone -> USB Typing -> Tap 'Check Connection'.
            4. Start auto-typing into Notepad or any active PC application!
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "REPLICA Windows Companion Download Link")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        activityContext.startActivity(Intent.createChooser(intent, "Share Companion Download Link"))
    }

    /**
     * Admin action: Upload/replace companion program from chosen Uri.
     */
    suspend fun uploadAndReplaceCompanion(
        fileUri: Uri,
        version: String,
        releaseNotes: String,
        adminEmail: String,
        serverBaseUrl: String = "http://127.0.0.1:3000"
    ): Result<CompanionInfo> = withContext(Dispatchers.IO) {
        try {
            // Read bytes from Uri
            val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Could not read file from selected URI"))

            // Save to local file
            FileOutputStream(localCompanionFile).use { it.write(bytes) }
            val sha256 = calculateFileSha256(localCompanionFile)

            val updatedInfo = CompanionInfo(
                version = version.ifBlank { "1.0.1" },
                protocolVersion = 1,
                minAppVersion = "1.0.0",
                fileName = "replica-companion.exe",
                fileSize = bytes.size.toLong(),
                sha256 = sha256,
                downloadUrl = "/download/replica-companion.exe",
                uploadedBy = adminEmail,
                lastUpdated = System.currentTimeMillis(),
                releaseNotes = releaseNotes
            )

            // Save to Firestore
            try {
                firestore.collection("settings").document("windows_companion")
                    .set(
                        mapOf(
                            "version" to updatedInfo.version,
                            "protocolVersion" to updatedInfo.protocolVersion,
                            "minAppVersion" to updatedInfo.minAppVersion,
                            "fileName" to updatedInfo.fileName,
                            "fileSize" to updatedInfo.fileSize,
                            "sha256" to updatedInfo.sha256,
                            "downloadUrl" to updatedInfo.downloadUrl,
                            "uploadedBy" to updatedInfo.uploadedBy,
                            "lastUpdated" to updatedInfo.lastUpdated,
                            "releaseNotes" to updatedInfo.releaseNotes
                        )
                    ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore write warning: ${e.message}")
            }

            // Also attempt HTTP upload to companion backend server if available
            try {
                val uploadUrl = URL("$serverBaseUrl/api/companion/upload")
                val connection = (uploadUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("x-companion-version", updatedInfo.version)
                    setRequestProperty("x-uploader-email", adminEmail)
                    setRequestProperty("x-release-notes", releaseNotes)
                    connectTimeout = 3000
                    readTimeout = 5000
                }

                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                Log.d(TAG, "Server companion upload HTTP code: $code")
            } catch (e: Exception) {
                Log.d(TAG, "Optional server upload skipped/deferred: ${e.message}")
            }

            Result.success(updatedInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Upload companion failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#define COMPANION_VERSION "1.1.0"
#define PROTOCOL_VERSION 1
#define LISTEN_PORT 8989
#define PHONE_PORT 8990
#define BUFFER_SIZE (1024 * 1024 * 2) // 2 MB buffer for large scripts

#ifdef _MSC_VER
#pragma comment(lib, "ws2_32.lib")
#endif

static volatile bool g_running = true;
static volatile bool g_typing_active = false;
static volatile bool g_typing_paused = false;
static volatile bool g_typing_stop_requested = false;
static volatile bool g_phone_connected = false;
static volatile SOCKET g_active_sock = INVALID_SOCKET;

static CRITICAL_SECTION g_send_cs;
static CRITICAL_SECTION g_diag_cs;
static HANDLE g_typing_thread = NULL;
static HANDLE g_adb_monitor_thread = NULL;

// Live ADB & Tunnel Diagnostic State reported to Console and Android App
static char g_adb_path[MAX_PATH] = "Not found";
static char g_adb_version[128] = "Unknown";
static char g_adb_devices_summary[256] = "No devices";
static char g_device_serial[128] = "";
static char g_device_auth_state[64] = "MISSING";
static bool g_device_authorized = false;
static char g_reverse_status[128] = "Not configured";
static char g_forward_status[128] = "Not configured";
static char g_active_tunnel_mode[64] = "Reverse (8989)";

typedef struct {
    SOCKET sock;
    char* text;
    int delayMs;
} TypingParams;

// Disable Windows Console QuickEdit Mode so clicking inside the console never freezes the companion
static void DisableConsoleQuickEdit(void) {
    HANDLE hInput = GetStdHandle(STD_INPUT_HANDLE);
    if (hInput != INVALID_HANDLE_VALUE) {
        DWORD prevMode = 0;
        if (GetConsoleMode(hInput, &prevMode)) {
            DWORD newMode = (prevMode & ~ENABLE_QUICK_EDIT_MODE) | ENABLE_EXTENDED_FLAGS;
            SetConsoleMode(hInput, newMode);
        }
    }
}

// Signal handler for graceful shutdown
BOOL WINAPI ConsoleHandler(DWORD signal) {
    if (signal == CTRL_C_EVENT || signal == CTRL_CLOSE_EVENT) {
        printf("\n[REPLICA Companion] Shutting down...\n");
        g_running = false;
        g_typing_stop_requested = true;
        if (g_active_sock != INVALID_SOCKET) {
            closesocket(g_active_sock);
        }
        return TRUE;
    }
    return FALSE;
}

// Emulate typing a UTF-16 character into the active Windows window
bool SendUnicodeChar(wchar_t ch) {
    INPUT inputs[2];
    ZeroMemory(inputs, sizeof(inputs));

    if (ch == L'\r') return true; // Ignore CR; handle LF as Return

    if (ch == L'\n') {
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].ki.wVk = VK_RETURN;
        inputs[0].ki.wScan = 0;
        inputs[0].ki.dwFlags = 0;

        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].ki.wVk = VK_RETURN;
        inputs[1].ki.wScan = 0;
        inputs[1].ki.dwFlags = KEYEVENTF_KEYUP;

        UINT sent = SendInput(2, inputs, sizeof(INPUT));
        return (sent == 2);
    }

    if (ch == L'\t') {
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].ki.wVk = VK_TAB;
        inputs[0].ki.wScan = 0;
        inputs[0].ki.dwFlags = 0;

        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].ki.wVk = VK_TAB;
        inputs[1].ki.wScan = 0;
        inputs[1].ki.dwFlags = KEYEVENTF_KEYUP;

        UINT sent = SendInput(2, inputs, sizeof(INPUT));
        return (sent == 2);
    }

    // Generic Unicode Key Down
    inputs[0].type = INPUT_KEYBOARD;
    inputs[0].ki.wVk = 0;
    inputs[0].ki.wScan = ch;
    inputs[0].ki.dwFlags = KEYEVENTF_UNICODE;

    // Generic Unicode Key Up
    inputs[1].type = INPUT_KEYBOARD;
    inputs[1].ki.wVk = 0;
    inputs[1].ki.wScan = ch;
    inputs[1].ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;

    UINT sent = SendInput(2, inputs, sizeof(INPUT));
    return (sent == 2);
}

// Convert UTF-8 buffer to UTF-16 wide string
wchar_t* Utf8ToUtf16(const char* utf8) {
    int len = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, NULL, 0);
    if (len <= 0) return NULL;
    wchar_t* wstr = (wchar_t*)malloc(len * sizeof(wchar_t));
    if (!wstr) return NULL;
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, wstr, len);
    return wstr;
}

// Thread-safe message sender
bool SendMessageLine(SOCKET sock, const char* msg) {
    if (sock == INVALID_SOCKET) return false;
    char buffer[8192];
    snprintf(buffer, sizeof(buffer), "%s\n", msg);
    int len = (int)strlen(buffer);

    EnterCriticalSection(&g_send_cs);
    int sent = send(sock, buffer, len, 0);
    LeaveCriticalSection(&g_send_cs);

    return (sent == len);
}

// Escape string for safe JSON inclusion
static void JsonEscapeString(const char* in, char* out, size_t outSize) {
    size_t j = 0;
    for (size_t i = 0; in[i] != '\0' && j < outSize - 2; i++) {
        char c = in[i];
        if (c == '\"' || c == '\\') {
            if (j + 2 >= outSize - 1) break;
            out[j++] = '\\';
            out[j++] = c;
        } else if (c == '\n') {
            if (j + 2 >= outSize - 1) break;
            out[j++] = ' ';
        } else if (c == '\r' || c == '\t') {
            out[j++] = ' ';
        } else if ((unsigned char)c >= 32) {
            out[j++] = c;
        }
    }
    out[j] = '\0';
}

// Helper to decode a 4-digit hex character sequence into an integer
static int ParseHex4(const char* s) {
    int val = 0;
    for (int i = 0; i < 4; i++) {
        char c = s[i];
        val <<= 4;
        if (c >= '0' && c <= '9') val |= (c - '0');
        else if (c >= 'a' && c <= 'f') val |= (c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') val |= (c - 'A' + 10);
        else return -1;
    }
    return val;
}

// Extract JSON string field value with support for \uXXXX unicode escapes
bool GetJsonStringField(const char* json, const char* field, char* out, size_t outSize) {
    char search[128];
    snprintf(search, sizeof(search), "\"%s\":", field);
    const char* pos = strstr(json, search);
    if (!pos) return false;

    pos += strlen(search);
    while (*pos == ' ' || *pos == '\t') pos++;

    if (*pos != '\"') return false;
    pos++; // Skip opening quote

    size_t i = 0;
    while (*pos && *pos != '\"' && i < outSize - 1) {
        if (*pos == '\\') {
            pos++;
            if (*pos == 'u') {
                pos++;
                int codepoint = ParseHex4(pos);
                if (codepoint >= 0) {
                    pos += 4;
                    if (codepoint <= 0x7F) {
                        if (i < outSize - 1) out[i++] = (char)codepoint;
                    } else if (codepoint <= 0x7FF) {
                        if (i < outSize - 2) {
                            out[i++] = (char)(0xC0 | ((codepoint >> 6) & 0x1F));
                            out[i++] = (char)(0x80 | (codepoint & 0x3F));
                        }
                    } else {
                        if (i < outSize - 3) {
                            out[i++] = (char)(0xE0 | ((codepoint >> 12) & 0x0F));
                            out[i++] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
                            out[i++] = (char)(0x80 | (codepoint & 0x3F));
                        }
                    }
                    continue;
                }
            } else if (*pos == 'n') {
                out[i++] = '\n';
                pos++;
                continue;
            } else if (*pos == 'r') {
                out[i++] = '\r';
                pos++;
                continue;
            } else if (*pos == 't') {
                out[i++] = '\t';
                pos++;
                continue;
            } else if (*pos == '\"') {
                out[i++] = '\"';
                pos++;
                continue;
            } else if (*pos == '\\') {
                out[i++] = '\\';
                pos++;
                continue;
            } else if (*pos == '/') {
                out[i++] = '/';
                pos++;
                continue;
            }
        }
        out[i++] = *pos;
        pos++;
    }
    out[i] = '\0';
    return true;
}

// Extract JSON integer field value
bool GetJsonIntField(const char* json, const char* field, int* out) {
    char search[128];
    snprintf(search, sizeof(search), "\"%s\":", field);
    const char* pos = strstr(json, search);
    if (!pos) return false;

    pos += strlen(search);
    while (*pos == ' ' || *pos == '\t') pos++;

    *out = atoi(pos);
    return true;
}

// Background typing thread procedure
DWORD WINAPI TypingThreadProc(LPVOID lpParam) {
    TypingParams* params = (TypingParams*)lpParam;
    SOCKET sock = params->sock;
    char* text = params->text;
    int delayMs = params->delayMs;

    g_typing_active = true;
    g_typing_paused = false;
    g_typing_stop_requested = false;

    wchar_t* wideText = Utf8ToUtf16(text);
    if (!wideText) {
        printf("[TYPING ERROR] Failed to decode UTF-8 text\n");
        SendMessageLine(sock, "{\"type\":\"ERROR\",\"message\":\"UTF-8 decode failed\"}");
        g_typing_active = false;
        free(text);
        free(params);
        return 1;
    }

    int totalChars = (int)wcslen(wideText);
    printf("[TYPING] Target length: %d chars. Delay: %d ms.\n", totalChars, delayMs);
    printf("[TYPING] Typing begins in 1.2s - ensure target window (e.g. Notepad) is focused!\n");
    Sleep(1200);

    int typedCount = 0;
    int failedInputCount = 0;

    for (int i = 0; i < totalChars; i++) {
        if (!g_running || g_typing_stop_requested) {
            printf("[TYPING] Typing cancelled by user request.\n");
            SendMessageLine(sock, "{\"type\":\"STOPPED\",\"message\":\"Typing cancelled\"}");
            break;
        }

        while (g_typing_paused && g_running && !g_typing_stop_requested) {
            Sleep(50);
        }

        if (!g_running || g_typing_stop_requested) {
            SendMessageLine(sock, "{\"type\":\"STOPPED\",\"message\":\"Typing cancelled\"}");
            break;
        }

        wchar_t ch = wideText[i];
        if (!SendUnicodeChar(ch)) {
            failedInputCount++;
            if (failedInputCount == 1 || failedInputCount % 20 == 0) {
                printf("[TYPING WARNING] SendInput reported failure (count: %d)\n", failedInputCount);
            }
        }
        typedCount++;

        // Send progress updates periodically or on last character
        if (i % 5 == 0 || i == totalChars - 1) {
            char progMsg[256];
            float pct = (float)typedCount / (float)totalChars;
            snprintf(progMsg, sizeof(progMsg),
                "{\"type\":\"PROGRESS\",\"currentIndex\":%d,\"totalChars\":%d,\"percent\":%.2f}",
                typedCount, totalChars, pct);
            SendMessageLine(sock, progMsg);
        }

        Sleep(delayMs > 0 ? delayMs : 25);
    }

    if (!g_typing_stop_requested && typedCount == totalChars) {
        if (failedInputCount > 0) {
            printf("[TYPING] Completed with %d SendInput warnings. Total typed: %d\n", failedInputCount, typedCount);
        } else {
            printf("[TYPING] Completed successfully! (%d chars typed)\n", typedCount);
        }
        char doneMsg[256];
        snprintf(doneMsg, sizeof(doneMsg), "{\"type\":\"DONE\",\"totalTyped\":%d}", typedCount);
        SendMessageLine(sock, doneMsg);
    }

    g_typing_active = false;
    free(wideText);
    free(text);
    free(params);
    return 0;
}

// Check if a file exists
static bool FileExists(const char* path) {
    DWORD dwAttrib = GetFileAttributesA(path);
    return (dwAttrib != INVALID_FILE_ATTRIBUTES && !(dwAttrib & FILE_ATTRIBUTE_DIRECTORY));
}

// Run a shell command and capture stdout/stderr into outBuf
static int RunCommandCapture(const char* cmdLine, char* outBuf, size_t outSize) {
    if (outBuf && outSize > 0) outBuf[0] = '\0';
    char fullCmd[1024];
    snprintf(fullCmd, sizeof(fullCmd), "%s 2>&1", cmdLine);
    FILE* pipe = _popen(fullCmd, "r");
    if (!pipe) return -1;

    size_t total = 0;
    char line[256];
    while (fgets(line, sizeof(line), pipe) != NULL) {
        size_t len = strlen(line);
        if (outBuf && total + len < outSize - 1) {
            memcpy(outBuf + total, line, len);
            total += len;
            outBuf[total] = '\0';
        }
    }
    return _pclose(pipe);
}

// Locate adb.exe across all standard Windows and Android SDK locations
static bool FindAdbPath(char* outPath, size_t maxLen) {
    // 1. Check directory next to companion executable and platform-tools subfolder
    char exePath[MAX_PATH];
    if (GetModuleFileNameA(NULL, exePath, MAX_PATH) > 0) {
        char* lastSlash = strrchr(exePath, '\\');
        if (lastSlash) {
            *lastSlash = '\0';
            snprintf(outPath, maxLen, "%s\\adb.exe", exePath);
            if (FileExists(outPath)) return true;

            snprintf(outPath, maxLen, "%s\\platform-tools\\adb.exe", exePath);
            if (FileExists(outPath)) return true;
        }
    }

    // 2. Check current working directory
    if (FileExists("adb.exe")) {
        snprintf(outPath, maxLen, "adb.exe");
        return true;
    }
    if (FileExists("platform-tools\\adb.exe")) {
        snprintf(outPath, maxLen, "platform-tools\\adb.exe");
        return true;
    }

    // 3. Check %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
    const char* localApp = getenv("LOCALAPPDATA");
    if (localApp) {
        snprintf(outPath, maxLen, "%s\\Android\\Sdk\\platform-tools\\adb.exe", localApp);
        if (FileExists(outPath)) return true;
    }

    // 4. Check %USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe and Downloads
    const char* userProfile = getenv("USERPROFILE");
    if (userProfile) {
        snprintf(outPath, maxLen, "%s\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe", userProfile);
        if (FileExists(outPath)) return true;

        snprintf(outPath, maxLen, "%s\\Downloads\\platform-tools\\adb.exe", userProfile);
        if (FileExists(outPath)) return true;

        snprintf(outPath, maxLen, "%s\\Downloads\\platform-tools-latest-windows\\platform-tools\\adb.exe", userProfile);
        if (FileExists(outPath)) return true;

        snprintf(outPath, maxLen, "%s\\Desktop\\platform-tools\\adb.exe", userProfile);
        if (FileExists(outPath)) return true;
    }

    // 5. Check %ANDROID_HOME% and %ANDROID_SDK_ROOT%
    const char* androidHome = getenv("ANDROID_HOME");
    if (androidHome) {
        snprintf(outPath, maxLen, "%s\\platform-tools\\adb.exe", androidHome);
        if (FileExists(outPath)) return true;
    }

    const char* sdkRoot = getenv("ANDROID_SDK_ROOT");
    if (sdkRoot) {
        snprintf(outPath, maxLen, "%s\\platform-tools\\adb.exe", sdkRoot);
        if (FileExists(outPath)) return true;
    }

    // 6. Check common C:\ paths
    const char* commonPaths[] = {
        "C:\\platform-tools\\adb.exe",
        "C:\\adb\\adb.exe",
        "C:\\Android\\platform-tools\\adb.exe",
        "C:\\Program Files (x86)\\Android\\android-sdk\\platform-tools\\adb.exe",
        "C:\\Program Files\\Android\\android-sdk\\platform-tools\\adb.exe"
    };
    for (int i = 0; i < (int)(sizeof(commonPaths) / sizeof(commonPaths[0])); i++) {
        if (FileExists(commonPaths[i])) {
            snprintf(outPath, maxLen, "%s", commonPaths[i]);
            return true;
        }
    }

    // 7. Check system PATH via SearchPathA
    char searchBuf[MAX_PATH];
    if (SearchPathA(NULL, "adb.exe", NULL, MAX_PATH, searchBuf, NULL) > 0) {
        snprintf(outPath, maxLen, "%s", searchBuf);
        return true;
    }

    return false;
}

// Parse `adb devices` output and determine device serial & authorization state
static void InspectAdbDevicesAndConfigureTunnels(bool verboseLog) {
    char adbPath[MAX_PATH];
    if (!FindAdbPath(adbPath, sizeof(adbPath))) {
        EnterCriticalSection(&g_diag_cs);
        snprintf(g_adb_path, sizeof(g_adb_path), "NOT_FOUND");
        snprintf(g_adb_version, sizeof(g_adb_version), "ADB not installed or not found");
        snprintf(g_adb_devices_summary, sizeof(g_adb_devices_summary), "adb.exe missing");
        snprintf(g_device_auth_state, sizeof(g_device_auth_state), "ADB_MISSING");
        g_device_authorized = false;
        LeaveCriticalSection(&g_diag_cs);

        if (verboseLog) {
            printf("\n[ADB DIAGNOSTIC] ERROR: adb.exe was not found on this PC!\n");
            printf("  WHY THE 'Allow USB debugging?' POPUP DID NOT APPEAR:\n");
            printf("  The Android authorization prompt is triggered ONLY when Windows ADB (adb.exe)\n");
            printf("  communicates with your phone over USB. Without adb.exe, the phone only sees power/data.\n");
            printf("  HOW TO FIX:\n");
            printf("  1. Download Android SDK Platform-Tools for Windows from:\n");
            printf("     https://developer.android.com/tools/releases/platform-tools\n");
            printf("  2. Extract adb.exe, AdbWinApi.dll, and AdbWinUsbApi.dll into the SAME folder\n");
            printf("     as replica-companion.exe (or C:\\platform-tools).\n\n");
        }
        return;
    }

    char cmd[MAX_PATH + 128];
    char out[2048];

    // 1. Get ADB version
    snprintf(cmd, sizeof(cmd), "\"%s\" version", adbPath);
    RunCommandCapture(cmd, out, sizeof(out));
    char firstLine[128] = "Android Debug Bridge";
    char* nl = strchr(out, '\n');
    if (nl) *nl = '\0';
    if (strlen(out) > 0) {
        snprintf(firstLine, sizeof(firstLine), "%s", out);
    }

    // 2. Ensure ADB server is started
    snprintf(cmd, sizeof(cmd), "\"%s\" start-server", adbPath);
    RunCommandCapture(cmd, out, sizeof(out));

    // 3. Query `adb devices`
    snprintf(cmd, sizeof(cmd), "\"%s\" devices", adbPath);
    RunCommandCapture(cmd, out, sizeof(out));

    char detectedSerial[128] = "";
    char detectedState[64] = "MISSING";
    char summaryBuf[256] = "";

    char* line = strtok(out, "\r\n");
    while (line != NULL) {
        // Skip header or daemon lines
        if (strstr(line, "List of devices") == NULL &&
            strstr(line, "* daemon") == NULL &&
            strlen(line) > 2) {
            char serial[128] = "";
            char state[64] = "";
            if (sscanf(line, "%127s %63s", serial, state) == 2) {
                if (strlen(summaryBuf) > 0) strncat(summaryBuf, "; ", sizeof(summaryBuf) - strlen(summaryBuf) - 1);
                char item[196];
                snprintf(item, sizeof(item), "%s (%s)", serial, state);
                strncat(summaryBuf, item, sizeof(summaryBuf) - strlen(summaryBuf) - 1);

                // Prefer a physical USB device over an emulator if multiple exist, or take first
                if (strlen(detectedSerial) == 0 || strcmp(state, "device") == 0) {
                    snprintf(detectedSerial, sizeof(detectedSerial), "%s", serial);
                    snprintf(detectedState, sizeof(detectedState), "%s", state);
                }
            }
        }
        line = strtok(NULL, "\r\n");
    }

    if (strlen(summaryBuf) == 0) {
        snprintf(summaryBuf, sizeof(summaryBuf), "No ADB devices attached");
    }

    bool isAuth = (strcmp(detectedState, "device") == 0);
    char revStatus[128] = "Not configured";
    char fwdStatus[128] = "Not configured";

    if (isAuth && strlen(detectedSerial) > 0) {
        // Configure reverse tunnel on 8989 using explicit -s <serial>
        snprintf(cmd, sizeof(cmd), "\"%s\" -s \"%s\" reverse tcp:%d tcp:%d", adbPath, detectedSerial, LISTEN_PORT, LISTEN_PORT);
        int r1 = RunCommandCapture(cmd, out, sizeof(out));
        if (r1 == 0) {
            snprintf(revStatus, sizeof(revStatus), "OK (tcp:%d -> tcp:%d on %s)", LISTEN_PORT, LISTEN_PORT, detectedSerial);
        } else {
            snprintf(revStatus, sizeof(revStatus), "FAILED (%s)", out);
        }

        // Configure forward tunnel on 8990 using explicit -s <serial>
        snprintf(cmd, sizeof(cmd), "\"%s\" -s \"%s\" forward tcp:%d tcp:%d", adbPath, detectedSerial, PHONE_PORT, PHONE_PORT);
        int r2 = RunCommandCapture(cmd, out, sizeof(out));
        if (r2 == 0) {
            snprintf(fwdStatus, sizeof(fwdStatus), "OK (tcp:%d -> tcp:%d on %s)", PHONE_PORT, PHONE_PORT, detectedSerial);
        } else {
            snprintf(fwdStatus, sizeof(fwdStatus), "FAILED (%s)", out);
        }
    }

    EnterCriticalSection(&g_diag_cs);
    snprintf(g_adb_path, sizeof(g_adb_path), "%s", adbPath);
    snprintf(g_adb_version, sizeof(g_adb_version), "%s", firstLine);
    snprintf(g_adb_devices_summary, sizeof(g_adb_devices_summary), "%s", summaryBuf);
    snprintf(g_device_serial, sizeof(g_device_serial), "%s", detectedSerial);
    snprintf(g_device_auth_state, sizeof(g_device_auth_state), "%s", detectedState);
    g_device_authorized = isAuth;
    snprintf(g_reverse_status, sizeof(g_reverse_status), "%s", revStatus);
    snprintf(g_forward_status, sizeof(g_forward_status), "%s", fwdStatus);
    LeaveCriticalSection(&g_diag_cs);

    if (verboseLog) {
        printf("[ADB DIAGNOSTIC] Path:    %s\n", adbPath);
        printf("[ADB DIAGNOSTIC] Version: %s\n", firstLine);
        printf("[ADB DIAGNOSTIC] Devices: %s\n", summaryBuf);

        if (strcmp(detectedState, "MISSING") == 0) {
            printf("[ADB STATUS] Phone NOT detected by ADB.\n");
            printf("  -> Check that you are using a USB DATA cable (not charge-only).\n");
            printf("  -> On your phone: Enable Developer Options -> Enable USB Debugging.\n");
            printf("  -> Switch USB Mode to 'File Transfer / MTP' or 'PTP' in the USB notification.\n");
            printf("  -> Check Windows Device Manager for 'Android Composite ADB Interface'.\n");
        } else if (strcmp(detectedState, "unauthorized") == 0) {
            printf("[ADB STATUS] Phone detected (%s) but UNAUTHORIZED!\n", detectedSerial);
            printf("  -> Unlock your phone screen now.\n");
            printf("  -> Look for the 'Allow USB debugging?' popup on your phone.\n");
            printf("  -> Check 'Always allow from this computer' and tap 'Allow'.\n");
            printf("  -> If the popup does not appear: go to Developer Options -> 'Revoke USB debugging authorizations',\n");
            printf("     then toggle USB Debugging OFF and ON.\n");
        } else if (strcmp(detectedState, "offline") == 0) {
            printf("[ADB STATUS] Phone (%s) is OFFLINE. Unplug and replug the USB cable.\n", detectedSerial);
        } else if (isAuth) {
            printf("[ADB STATUS] Phone (%s) is AUTHORIZED.\n", detectedSerial);
            printf("[ADB TUNNEL] Reverse 8989: %s\n", revStatus);
            printf("[ADB TUNNEL] Forward 8990: %s\n", fwdStatus);
        }
    }
}

// Build and sendHANDSHAKE_ACK with full diagnostic metadata
static void SendHandshakeAckWithDiagnostics(SOCKET sock) {
    char escPath[512], escVer[256], escDev[512], escRev[256], escFwd[256], escMode[128];
    EnterCriticalSection(&g_diag_cs);
    JsonEscapeString(g_adb_path, escPath, sizeof(escPath));
    JsonEscapeString(g_adb_version, escVer, sizeof(escVer));
    JsonEscapeString(g_adb_devices_summary, escDev, sizeof(escDev));
    JsonEscapeString(g_reverse_status, escRev, sizeof(escRev));
    JsonEscapeString(g_forward_status, escFwd, sizeof(escFwd));
    JsonEscapeString(g_active_tunnel_mode, escMode, sizeof(escMode));
    bool auth = g_device_authorized;
    LeaveCriticalSection(&g_diag_cs);

    char ack[2048];
    snprintf(ack, sizeof(ack),
        "{\"type\":\"HANDSHAKE_ACK\",\"protocolVersion\":%d,\"server\":\"replica_windows_companion\","
        "\"companionVersion\":\"%s\",\"os\":\"Windows\",\"status\":\"SYNCED\","
        "\"adbPath\":\"%s\",\"adbVersion\":\"%s\",\"adbDevices\":\"%s\","
        "\"deviceAuthorized\":%s,\"reverseStatus\":\"%s\",\"forwardStatus\":\"%s\",\"tunnelMode\":\"%s\"}",
        PROTOCOL_VERSION, COMPANION_VERSION,
        escPath, escVer, escDev,
        auth ? "true" : "false", escRev, escFwd, escMode);
    SendMessageLine(sock, ack);
}

// Handle an active socket session (either accepted on 8989 or dialed to 8990)
static void HandleClientSession(SOCKET clientSock, const char* modeLabel) {
    EnterCriticalSection(&g_diag_cs);
    snprintf(g_active_tunnel_mode, sizeof(g_active_tunnel_mode), "%s", modeLabel);
    LeaveCriticalSection(&g_diag_cs);

    g_active_sock = clientSock;
    g_phone_connected = true;

    char* recvBuf = (char*)malloc(BUFFER_SIZE);
    if (!recvBuf) {
        g_phone_connected = false;
        g_active_sock = INVALID_SOCKET;
        closesocket(clientSock);
        return;
    }

    int accumulated = 0;

    while (g_running) {
        int bytesRecv = recv(clientSock, recvBuf + accumulated, (int)(BUFFER_SIZE - 1 - accumulated), 0);
        if (bytesRecv <= 0) {
            printf("[DISCONNECTED] Phone connection closed (%s).\n", modeLabel);
            g_typing_stop_requested = true;
            break;
        }

        accumulated += bytesRecv;
        recvBuf[accumulated] = '\0';

        // Process complete lines (newline delimited JSON)
        char* lineStart = recvBuf;
        char* lineEnd;
        while ((lineEnd = strchr(lineStart, '\n')) != NULL) {
            *lineEnd = '\0';

            if (lineEnd > lineStart && *(lineEnd - 1) == '\r') {
                *(lineEnd - 1) = '\0';
            }

            if (strlen(lineStart) > 0) {
                char type[64] = {0};
                GetJsonStringField(lineStart, "type", type, sizeof(type));

                if (strcmp(type, "HANDSHAKE_SYN") == 0) {
                    int proto = 0;
                    GetJsonIntField(lineStart, "protocolVersion", &proto);
                    printf("[HANDSHAKE] Received SYN (Protocol %d) via %s. Sending ACK...\n", proto, modeLabel);
                    SendHandshakeAckWithDiagnostics(clientSock);
                    printf("[SYNCED] Handshake confirmed! Phone and PC are SYNCED and Ready to Type.\n");
                }
                else if (strcmp(type, "PING") == 0) {
                    SendMessageLine(clientSock, "{\"type\":\"PONG\",\"status\":\"OK\"}");
                }
                else if (strcmp(type, "CMD_START") == 0) {
                    char* text = (char*)malloc(BUFFER_SIZE);
                    if (text) {
                        text[0] = '\0';
                        int delay = 25;
                        GetJsonStringField(lineStart, "text", text, BUFFER_SIZE);
                        GetJsonIntField(lineStart, "delayMs", &delay);
                        printf("[COMMAND] START requested. Length: %zu bytes, delay: %d ms\n", strlen(text), delay);

                        if (g_typing_active) {
                            g_typing_stop_requested = true;
                            if (g_typing_thread != NULL) {
                                WaitForSingleObject(g_typing_thread, 1000);
                                CloseHandle(g_typing_thread);
                                g_typing_thread = NULL;
                            }
                        }

                        TypingParams* p = (TypingParams*)malloc(sizeof(TypingParams));
                        p->sock = clientSock;
                        p->text = text;
                        p->delayMs = delay;

                        g_typing_thread = CreateThread(NULL, 0, TypingThreadProc, p, 0, NULL);
                    } else {
                        SendMessageLine(clientSock, "{\"type\":\"ERROR\",\"message\":\"Out of memory for text payload\"}");
                    }
                }
                else if (strcmp(type, "CMD_PAUSE") == 0) {
                    printf("[COMMAND] PAUSE received.\n");
                    g_typing_paused = true;
                    SendMessageLine(clientSock, "{\"type\":\"PAUSED\"}");
                }
                else if (strcmp(type, "CMD_RESUME") == 0) {
                    printf("[COMMAND] RESUME received.\n");
                    g_typing_paused = false;
                    SendMessageLine(clientSock, "{\"type\":\"RESUMED\"}");
                }
                else if (strcmp(type, "CMD_STOP") == 0) {
                    printf("[COMMAND] STOP received.\n");
                    g_typing_stop_requested = true;
                    SendMessageLine(clientSock, "{\"type\":\"STOPPED\"}");
                }
                else if (strcmp(type, "STATUS_CHECK") == 0) {
                    SendHandshakeAckWithDiagnostics(clientSock);
                }
            }

            lineStart = lineEnd + 1;
        }

        int remaining = (int)(recvBuf + accumulated - lineStart);
        if (remaining > 0 && lineStart > recvBuf) {
            memmove(recvBuf, lineStart, remaining);
            accumulated = remaining;
        } else if (remaining == 0) {
            accumulated = 0;
        }
    }

    free(recvBuf);
    g_phone_connected = false;
    g_active_sock = INVALID_SOCKET;
    closesocket(clientSock);
}

// Attempt to actively dial the phone's server on 127.0.0.1:8990 via `adb forward tcp:8990 tcp:8990`
static SOCKET TryDialPhoneForwardTunnel(void) {
    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) return INVALID_SOCKET;

    // Set non-blocking for fast 800ms connect timeout
    u_long mode = 1;
    ioctlsocket(sock, FIONBIO, &mode);

    struct sockaddr_in addr;
    ZeroMemory(&addr, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PHONE_PORT);
    inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);

    connect(sock, (struct sockaddr*)&addr, sizeof(addr));

    fd_set writeSet;
    FD_ZERO(&writeSet);
    FD_SET(sock, &writeSet);
    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 800000; // 800 ms

    int sel = select(0, NULL, &writeSet, NULL, &tv);
    if (sel <= 0) {
        closesocket(sock);
        return INVALID_SOCKET;
    }

    int soError = 0;
    int len = sizeof(soError);
    getsockopt(sock, SOL_SOCKET, SO_ERROR, (char*)&soError, &len);
    if (soError != 0) {
        closesocket(sock);
        return INVALID_SOCKET;
    }

    // Restore blocking mode
    mode = 0;
    ioctlsocket(sock, FIONBIO, &mode);
    return sock;
}

// Background thread that continuously monitors ADB state, re-establishes tunnels, and dials 8990 if needed
DWORD WINAPI AdbMonitorThreadProc(LPVOID lpParam) {
    (void)lpParam;
    char lastLoggedState[64] = "";

    while (g_running) {
        for (int i = 0; i < 30 && g_running; i++) {
            Sleep(100);
        }
        if (!g_running) break;

        if (!g_phone_connected) {
            InspectAdbDevicesAndConfigureTunnels(false);

            char curState[64];
            char curSerial[128];
            bool auth = false;
            EnterCriticalSection(&g_diag_cs);
            snprintf(curState, sizeof(curState), "%s", g_device_auth_state);
            snprintf(curSerial, sizeof(curSerial), "%s", g_device_serial);
            auth = g_device_authorized;
            LeaveCriticalSection(&g_diag_cs);

            if (strcmp(curState, lastLoggedState) != 0) {
                snprintf(lastLoggedState, sizeof(lastLoggedState), "%s", curState);
                if (strcmp(curState, "unauthorized") == 0) {
                    printf("\n[ADB MONITOR] Phone (%s) detected as UNAUTHORIZED.\n", curSerial);
                    printf("  -> Unlock your phone and tap 'Allow' on the 'Allow USB debugging?' prompt!\n");
                } else if (strcmp(curState, "device") == 0) {
                    printf("\n[ADB MONITOR] Phone (%s) AUTHORIZED! Tunnels configured on ports %d & %d.\n",
                        curSerial, LISTEN_PORT, PHONE_PORT);
                } else if (strcmp(curState, "MISSING") == 0) {
                    printf("\n[ADB MONITOR] Waiting for Android phone on USB...\n");
                }
            }

            // If phone is authorized (or even if user ran manual adb forward), try dialing 127.0.0.1:8990
            if (!g_phone_connected && (auth || strcmp(curState, "ADB_MISSING") == 0)) {
                SOCKET fwdSock = TryDialPhoneForwardTunnel();
                if (fwdSock != INVALID_SOCKET && !g_phone_connected) {
                    printf("\n[CONNECTED] Established connection to phone via Forward Tunnel (127.0.0.1:%d)\n", PHONE_PORT);
                    HandleClientSession(fwdSock, "Forward Tunnel (8990)");
                } else if (fwdSock != INVALID_SOCKET) {
                    closesocket(fwdSock);
                }
            }
        }
    }
    return 0;
}

int main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    InitializeCriticalSection(&g_send_cs);
    InitializeCriticalSection(&g_diag_cs);
    DisableConsoleQuickEdit();
    SetConsoleTitleA("REPLICA Windows Companion v1.1.0");
    SetConsoleCtrlHandler(ConsoleHandler, TRUE);

    printf("================================================================\n");
    printf("   REPLICA - Windows Companion v%s\n", COMPANION_VERSION);
    printf("   \"I replicate keyboard\" - USB Auto-Typing & ADB Bridge\n");
    printf("================================================================\n");
    printf("[INFO] Protocol Version : %d\n", PROTOCOL_VERSION);
    printf("[INFO] Reverse Port     : %d (PC listens on 127.0.0.1:%d)\n", LISTEN_PORT, LISTEN_PORT);
    printf("[INFO] Forward Port     : %d (PC dials phone on 127.0.0.1:%d)\n", PHONE_PORT, PHONE_PORT);
    printf("----------------------------------------------------------------\n");

    WSADATA wsaData;
    int res = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (res != 0) {
        printf("[ERROR] WSAStartup failed: %d\n", res);
        return 1;
    }

    // Initial verbose ADB inspection and tunnel configuration
    InspectAdbDevicesAndConfigureTunnels(true);
    printf("----------------------------------------------------------------\n");

    SOCKET listenSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSock == INVALID_SOCKET) {
        printf("[ERROR] socket() failed: %d\n", WSAGetLastError());
        WSACleanup();
        return 1;
    }

    int opt = 1;
    setsockopt(listenSock, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt));

    struct sockaddr_in serverAddr;
    ZeroMemory(&serverAddr, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = htonl(INADDR_ANY);
    serverAddr.sin_port = htons(LISTEN_PORT);

    if (bind(listenSock, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        printf("[ERROR] bind() failed on port %d (WSAError %d). Is another instance running?\n",
            LISTEN_PORT, WSAGetLastError());
        closesocket(listenSock);
        WSACleanup();
        return 1;
    }

    if (listen(listenSock, 4) == SOCKET_ERROR) {
        printf("[ERROR] listen() failed: %d\n", WSAGetLastError());
        closesocket(listenSock);
        WSACleanup();
        return 1;
    }

    printf("[READY] Listening on 127.0.0.1:%d and monitoring ADB forward tunnel 127.0.0.1:%d...\n",
        LISTEN_PORT, PHONE_PORT);

    // Start background ADB & Forward-Tunnel monitor thread
    g_adb_monitor_thread = CreateThread(NULL, 0, AdbMonitorThreadProc, NULL, 0, NULL);

    while (g_running) {
        fd_set readSet;
        FD_ZERO(&readSet);
        FD_SET(listenSock, &readSet);
        struct timeval tv;
        tv.tv_sec = 1;
        tv.tv_usec = 0;

        int sel = select(0, &readSet, NULL, NULL, &tv);
        if (sel <= 0) {
            continue;
        }

        struct sockaddr_in clientAddr;
        int clientLen = sizeof(clientAddr);
        SOCKET clientSock = accept(listenSock, (struct sockaddr*)&clientAddr, &clientLen);

        if (clientSock == INVALID_SOCKET) {
            if (!g_running) break;
            Sleep(100);
            continue;
        }

        if (g_phone_connected) {
            // Already have an active session; close duplicate or replace if needed
            closesocket(clientSock);
            continue;
        }

        char clientIp[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &(clientAddr.sin_addr), clientIp, INET_ADDRSTRLEN);
        printf("\n[CONNECTED] Phone connected via Reverse Tunnel from %s:%d\n",
            clientIp, ntohs(clientAddr.sin_port));

        HandleClientSession(clientSock, "Reverse Tunnel (8989)");
    }

    if (g_typing_thread != NULL) {
        g_typing_stop_requested = true;
        WaitForSingleObject(g_typing_thread, 1000);
        CloseHandle(g_typing_thread);
    }

    if (g_adb_monitor_thread != NULL) {
        WaitForSingleObject(g_adb_monitor_thread, 1500);
        CloseHandle(g_adb_monitor_thread);
    }

    closesocket(listenSock);
    DeleteCriticalSection(&g_send_cs);
    DeleteCriticalSection(&g_diag_cs);
    WSACleanup();
    printf("[EXIT] Companion terminated.\n");
    return 0;
}

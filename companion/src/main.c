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

#pragma comment(lib, "ws2_32.lib")

static volatile bool g_running = true;
static volatile bool g_typing_active = false;
static volatile bool g_typing_paused = false;
static volatile bool g_typing_stop_requested = false;

static CRITICAL_SECTION g_send_cs;
static HANDLE g_typing_thread = NULL;

typedef struct {
    SOCKET sock;
    char* text;
    int delayMs;
} TypingParams;

// Signal handler for graceful shutdown
BOOL WINAPI ConsoleHandler(DWORD signal) {
    if (signal == CTRL_C_EVENT || signal == CTRL_CLOSE_EVENT) {
        printf("\n[REPLICA Companion] Shutting down...\n");
        g_running = false;
        g_typing_stop_requested = true;
        return TRUE;
    }
    return FALSE;
}

// Emulate typing a UTF-16 character into the active Windows window
// Returns true if SendInput succeeded, false otherwise
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
    char buffer[4096];
    snprintf(buffer, sizeof(buffer), "%s\n", msg);
    int len = (int)strlen(buffer);

    EnterCriticalSection(&g_send_cs);
    int sent = send(sock, buffer, len, 0);
    LeaveCriticalSection(&g_send_cs);

    return (sent == len);
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
                    // Encode codepoint into UTF-8 bytes
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
    printf("[TYPING] Typing begins in 1.2s - ensure target window is focused!\n");
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

// Locate adb.exe or return false
static bool FindAdbPath(char* outPath, size_t maxLen) {
    // 1. Check directory next to companion executable
    char exePath[MAX_PATH];
    if (GetModuleFileNameA(NULL, exePath, MAX_PATH) > 0) {
        char* lastSlash = strrchr(exePath, '\\');
        if (lastSlash) {
            *lastSlash = '\0';
            snprintf(outPath, maxLen, "%s\\adb.exe", exePath);
            if (FileExists(outPath)) return true;
        }
    }

    // 2. Check current directory
    if (FileExists("adb.exe")) {
        snprintf(outPath, maxLen, "adb.exe");
        return true;
    }

    // 3. Check %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
    const char* localApp = getenv("LOCALAPPDATA");
    if (localApp) {
        snprintf(outPath, maxLen, "%s\\Android\\Sdk\\platform-tools\\adb.exe", localApp);
        if (FileExists(outPath)) return true;
    }

    // 4. Check %ANDROID_HOME%\platform-tools\adb.exe
    const char* androidHome = getenv("ANDROID_HOME");
    if (androidHome) {
        snprintf(outPath, maxLen, "%s\\platform-tools\\adb.exe", androidHome);
        if (FileExists(outPath)) return true;
    }

    // 5. Check %ANDROID_SDK_ROOT%\platform-tools\adb.exe
    const char* sdkRoot = getenv("ANDROID_SDK_ROOT");
    if (sdkRoot) {
        snprintf(outPath, maxLen, "%s\\platform-tools\\adb.exe", sdkRoot);
        if (FileExists(outPath)) return true;
    }

    // 6. Check system PATH via SearchPathA
    char searchBuf[MAX_PATH];
    if (SearchPathA(NULL, "adb.exe", NULL, MAX_PATH, searchBuf, NULL) > 0) {
        snprintf(outPath, maxLen, "%s", searchBuf);
        return true;
    }

    return false;
}

// Run ADB port forwarding and reversing commands, and clear any accidental "Wait for debugger" setting on phone
static void SetupAdbTunnels(void) {
    char adbPath[MAX_PATH];
    printf("[ADB SETUP] Checking for adb...\n");
    if (FindAdbPath(adbPath, sizeof(adbPath))) {
        printf("[ADB SETUP] Found ADB at: %s\n", adbPath);

        char cmdClearDebug[MAX_PATH + 96];
        char cmdClearWait[MAX_PATH + 96];
        char cmdClearAm[MAX_PATH + 96];
        char cmd1[MAX_PATH + 64];
        char cmd2[MAX_PATH + 64];

        // Ensure Android does not block replica_kspp on startup with "Waiting For Debugger"
        snprintf(cmdClearDebug, sizeof(cmdClearDebug), "\"%s\" shell settings delete global debug_app >nul 2>nul", adbPath);
        snprintf(cmdClearWait, sizeof(cmdClearWait), "\"%s\" shell settings put global wait_for_debugger 0 >nul 2>nul", adbPath);
        snprintf(cmdClearAm, sizeof(cmdClearAm), "\"%s\" shell am clear-debug-app >nul 2>nul", adbPath);
        system(cmdClearDebug);
        system(cmdClearWait);
        system(cmdClearAm);

        snprintf(cmd1, sizeof(cmd1), "\"%s\" reverse tcp:8989 tcp:8989", adbPath);
        snprintf(cmd2, sizeof(cmd2), "\"%s\" forward tcp:8990 tcp:8990", adbPath);

        printf("[ADB SETUP] Running: %s\n", cmd1);
        int r1 = system(cmd1);
        printf("[ADB SETUP] Running: %s\n", cmd2);
        int r2 = system(cmd2);

        if (r1 == 0 && r2 == 0) {
            printf("[ADB SETUP] Port tunnels configured & debugger-wait lock cleared!\n");
        } else {
            printf("[ADB SETUP] Note: Make sure your Android phone is connected and USB debugging is authorized.\n");
        }
    } else {
        printf("[ADB SETUP] adb.exe was not detected automatically.\n");
        printf("[ADB SETUP] If auto-connect fails, run these manual commands in cmd/powershell:\n");
        printf("    adb shell am clear-debug-app\n");
        printf("    adb reverse tcp:8989 tcp:8989\n");
        printf("    adb forward tcp:8990 tcp:8990\n");
    }
}

int main(int argc, char* argv[]) {
    InitializeCriticalSection(&g_send_cs);
    SetConsoleTitleA("REPLICA Windows Companion v1.1.0");
    SetConsoleCtrlHandler(ConsoleHandler, TRUE);

    printf("========================================================\n");
    printf("   REPLICA - Windows Companion v%s\n", COMPANION_VERSION);
    printf("   \"I replicate keyboard\" - USB Auto-Typing Bridge\n");
    printf("========================================================\n");
    printf("[INFO] Protocol version: %d\n", PROTOCOL_VERSION);
    printf("[INFO] Listening on TCP port %d\n", LISTEN_PORT);
    printf("[INFO] Connect Android phone via USB cable and tap 'Check Connection'.\n");
    printf("--------------------------------------------------------\n");

    // Initialize Winsock
    WSADATA wsaData;
    int res = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (res != 0) {
        printf("[ERROR] WSAStartup failed: %d\n", res);
        return 1;
    }

    // Try setting up ADB tunnels
    SetupAdbTunnels();
    printf("--------------------------------------------------------\n");

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
        printf("[ERROR] bind() failed on port %d: %d\n", LISTEN_PORT, WSAGetLastError());
        closesocket(listenSock);
        WSACleanup();
        return 1;
    }

    if (listen(listenSock, 1) == SOCKET_ERROR) {
        printf("[ERROR] listen() failed: %d\n", WSAGetLastError());
        closesocket(listenSock);
        WSACleanup();
        return 1;
    }

    printf("[READY] Server ready. Waiting for phone connection on port %d...\n", LISTEN_PORT);

    char* recvBuf = (char*)malloc(BUFFER_SIZE);
    if (!recvBuf) {
        printf("[FATAL] Out of memory for receive buffer\n");
        closesocket(listenSock);
        WSACleanup();
        return 1;
    }

    while (g_running) {
        struct sockaddr_in clientAddr;
        int clientLen = sizeof(clientAddr);
        SOCKET clientSock = accept(listenSock, (struct sockaddr*)&clientAddr, &clientLen);

        if (clientSock == INVALID_SOCKET) {
            if (!g_running) break;
            Sleep(100);
            continue;
        }

        char clientIp[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &(clientAddr.sin_addr), clientIp, INET_ADDRSTRLEN);
        printf("\n[CONNECTED] Phone connected from %s:%d\n", clientIp, ntohs(clientAddr.sin_port));

        int accumulated = 0;

        while (g_running) {
            int bytesRecv = recv(clientSock, recvBuf + accumulated, (int)(BUFFER_SIZE - 1 - accumulated), 0);
            if (bytesRecv <= 0) {
                printf("[DISCONNECTED] Phone disconnected.\n");
                // If typing was running, signal stop
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

                // Trim trailing CR
                if (lineEnd > lineStart && *(lineEnd - 1) == '\r') {
                    *(lineEnd - 1) = '\0';
                }

                if (strlen(lineStart) > 0) {
                    char type[64] = {0};
                    GetJsonStringField(lineStart, "type", type, sizeof(type));

                    if (strcmp(type, "HANDSHAKE_SYN") == 0) {
                        int proto = 0;
                        GetJsonIntField(lineStart, "protocolVersion", &proto);
                        printf("[HANDSHAKE] Received SYN (Protocol %d). Sending ACK...\n", proto);

                        char ack[512];
                        snprintf(ack, sizeof(ack),
                            "{\"type\":\"HANDSHAKE_ACK\",\"protocolVersion\":%d,\"server\":\"replica_windows_companion\",\"companionVersion\":\"%s\",\"os\":\"Windows\",\"status\":\"SYNCED\"}",
                            PROTOCOL_VERSION, COMPANION_VERSION);
                        SendMessageLine(clientSock, ack);
                        printf("[SYNCED] Handshake confirmed. Device is Synced!\n");
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
                            printf("[COMMAND] START requested. Length: %zu chars, delay: %d ms\n", strlen(text), delay);

                            // Cancel any running typing thread
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
                        char statusMsg[256];
                        snprintf(statusMsg, sizeof(statusMsg),
                            "{\"type\":\"STATUS_RES\",\"status\":\"SYNCED\",\"companionVersion\":\"%s\",\"typingActive\":%s}",
                            COMPANION_VERSION, g_typing_active ? "true" : "false");
                        SendMessageLine(clientSock, statusMsg);
                    }
                }

                lineStart = lineEnd + 1;
            }

            // Shift remaining partial line to beginning
            int remaining = (int)(recvBuf + accumulated - lineStart);
            if (remaining > 0 && lineStart > recvBuf) {
                memmove(recvBuf, lineStart, remaining);
                accumulated = remaining;
            } else if (remaining == 0) {
                accumulated = 0;
            }
        }

        closesocket(clientSock);
    }

    if (g_typing_thread != NULL) {
        g_typing_stop_requested = true;
        WaitForSingleObject(g_typing_thread, 1000);
        CloseHandle(g_typing_thread);
    }

    free(recvBuf);
    closesocket(listenSock);
    DeleteCriticalSection(&g_send_cs);
    WSACleanup();
    printf("[EXIT] Companion terminated.\n");
    return 0;
}

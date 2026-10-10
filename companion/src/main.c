#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#define COMPANION_VERSION "1.0.0"
#define PROTOCOL_VERSION 1
#define DEFAULT_PORT 8989
#define BUFFER_SIZE 65536

#pragma comment(lib, "ws2_32.lib")

static volatile bool g_running = true;
static volatile bool g_typing_active = false;
static volatile bool g_typing_paused = false;
static volatile bool g_typing_stop_requested = false;

// Signal handler for graceful exit
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
void SendUnicodeChar(wchar_t ch) {
    INPUT inputs[2];
    ZeroMemory(inputs, sizeof(inputs));

    if (ch == L'\r') return; // ignore CR, handle LF as Return

    if (ch == L'\n') {
        // Virtual Enter key
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].ki.wVk = VK_RETURN;
        inputs[0].ki.wScan = 0;
        inputs[0].ki.dwFlags = 0;

        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].ki.wVk = VK_RETURN;
        inputs[1].ki.wScan = 0;
        inputs[1].ki.dwFlags = KEYEVENTF_KEYUP;
        SendInput(2, inputs, sizeof(INPUT));
        return;
    }

    if (ch == L'\t') {
        // Virtual Tab key
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].ki.wVk = VK_TAB;
        inputs[0].ki.wScan = 0;
        inputs[0].ki.dwFlags = 0;

        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].ki.wVk = VK_TAB;
        inputs[1].ki.wScan = 0;
        inputs[1].ki.dwFlags = KEYEVENTF_KEYUP;
        SendInput(2, inputs, sizeof(INPUT));
        return;
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

    SendInput(2, inputs, sizeof(INPUT));
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

// Extract JSON string field value (simple parser)
bool GetJsonStringField(const char* json, const char* field, char* out, size_t outSize) {
    char search[128];
    snprintf(search, sizeof(search), "\"%s\":", field);
    const char* pos = strstr(json, search);
    if (!pos) return false;

    pos += strlen(search);
    while (*pos == ' ' || *pos == '\t') pos++;

    if (*pos != '\"') return false;
    pos++; // skip opening quote

    size_t i = 0;
    while (*pos && *pos != '\"' && i < outSize - 1) {
        if (*pos == '\\' && *(pos + 1)) {
            pos++;
            if (*pos == 'n') out[i++] = '\n';
            else if (*pos == 'r') out[i++] = '\r';
            else if (*pos == 't') out[i++] = '\t';
            else if (*pos == '\"') out[i++] = '\"';
            else if (*pos == '\\') out[i++] = '\\';
            else out[i++] = *pos;
        } else {
            out[i++] = *pos;
        }
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

// Send response line over socket
bool SendMessageLine(SOCKET sock, const char* msg) {
    char buffer[4096];
    snprintf(buffer, sizeof(buffer), "%s\n", msg);
    int len = (int)strlen(buffer);
    int sent = send(sock, buffer, len, 0);
    return sent == len;
}

// Execute typing loop
void ExecuteTyping(SOCKET clientSock, const char* textToType, int delayMs) {
    wchar_t* wideText = Utf8ToUtf16(textToType);
    if (!wideText) {
        SendMessageLine(clientSock, "{\"type\":\"ERROR\",\"message\":\"UTF-8 decode failed\"}");
        return;
    }

    int totalChars = (int)wcslen(wideText);
    printf("[TYPING] Target length: %d chars. Delay: %d ms.\n", totalChars, delayMs);
    printf("[TYPING] Typing begins in 1.5s - please ensure the target window is active!\n");
    Sleep(1500);

    g_typing_active = true;
    g_typing_paused = false;
    g_typing_stop_requested = false;

    int typedCount = 0;
    for (int i = 0; i < totalChars; i++) {
        if (!g_running || g_typing_stop_requested) {
            printf("[TYPING] Typing cancelled by user.\n");
            SendMessageLine(clientSock, "{\"type\":\"STOPPED\",\"message\":\"Typing cancelled\"}");
            break;
        }

        while (g_typing_paused && g_running && !g_typing_stop_requested) {
            Sleep(100);
        }

        wchar_t ch = wideText[i];
        SendUnicodeChar(ch);
        typedCount++;

        // Send progress updates periodically or on last character
        if (i % 5 == 0 || i == totalChars - 1) {
            char progMsg[256];
            float pct = (float)typedCount / (float)totalChars;
            snprintf(progMsg, sizeof(progMsg),
                "{\"type\":\"PROGRESS\",\"currentIndex\":%d,\"totalChars\":%d,\"percent\":%.2f}",
                typedCount, totalChars, pct);
            SendMessageLine(clientSock, progMsg);
        }

        Sleep(delayMs > 0 ? delayMs : 25);
    }

    if (!g_typing_stop_requested && typedCount == totalChars) {
        printf("[TYPING] Completed successfully! (%d chars typed)\n", typedCount);
        char doneMsg[256];
        snprintf(doneMsg, sizeof(doneMsg), "{\"type\":\"DONE\",\"totalTyped\":%d}", typedCount);
        SendMessageLine(clientSock, doneMsg);
    }

    g_typing_active = false;
    free(wideText);
}

int main(int argc, char* argv[]) {
    SetConsoleTitleA("REPLICA Windows Companion v1.0.0");
    SetConsoleCtrlHandler(ConsoleHandler, TRUE);

    printf("========================================================\n");
    printf("   REPLICA - Windows Companion v%s\n", COMPANION_VERSION);
    printf("   \"I replicate keyboard\" - USB Auto-Typing Bridge\n");
    printf("========================================================\n");
    printf("[INFO] Protocol version: %d\n", PROTOCOL_VERSION);
    printf("[INFO] Listening on TCP port %d\n", DEFAULT_PORT);
    printf("[INFO] Connect your Android phone via USB cable and tap 'Check Connection'.\n");
    printf("--------------------------------------------------------\n");

    // Initialize Winsock
    WSADATA wsaData;
    int res = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (res != 0) {
        printf("[ERROR] WSAStartup failed: %d\n", res);
        return 1;
    }

    SOCKET listenSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSock == INVALID_SOCKET) {
        printf("[ERROR] socket() failed: %d\n", WSAGetLastError());
        WSACleanup();
        return 1;
    }

    // Set reuse address
    int opt = 1;
    setsockopt(listenSock, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt));

    struct sockaddr_in serverAddr;
    ZeroMemory(&serverAddr, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = htonl(INADDR_ANY);
    serverAddr.sin_port = htons(DEFAULT_PORT);

    if (bind(listenSock, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        printf("[ERROR] bind() failed on port %d: %d\n", DEFAULT_PORT, WSAGetLastError());
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

    printf("[READY] Server ready. Waiting for phone connection...\n");

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

        char recvBuf[BUFFER_SIZE];
        int accumulated = 0;

        while (g_running) {
            int bytesRecv = recv(clientSock, recvBuf + accumulated, (int)(sizeof(recvBuf) - 1 - accumulated), 0);
            if (bytesRecv <= 0) {
                printf("[DISCONNECTED] Phone disconnected.\n");
                break;
            }

            accumulated += bytesRecv;
            recvBuf[accumulated] = '\0';

            // Process lines (newline delimited JSON)
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
                        char text[BUFFER_SIZE] = {0};
                        int delay = 25;
                        GetJsonStringField(lineStart, "text", text, sizeof(text));
                        GetJsonIntField(lineStart, "delayMs", &delay);
                        printf("[COMMAND] START requested. Length: %zu chars, delay: %d ms\n", strlen(text), delay);
                        ExecuteTyping(clientSock, text, delay);
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

    closesocket(listenSock);
    WSACleanup();
    printf("[EXIT] Companion terminated.\n");
    return 0;
}

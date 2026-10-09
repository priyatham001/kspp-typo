# replica_kspp

> *"I replicate keyboard"*

Server-controlled Bluetooth HID keyboard and auto-typer Android application with 8-hour access control, Google Sign-In, and Super Admin Management.

## Overview

**replica_kspp** (`com.aistudio.pskbtauto.kbapp`) is a native Android application built with Jetpack Compose, Kotlin Coroutines, Room DB, and Firebase Authentication/Firestore. It emulates a standard 101-key USB/Bluetooth HID keyboard, transforming incoming text commands into raw HID byte reports sent over standard Bluetooth HID RFCOMM profiles.

## Key Features

- **Bluetooth HID Keyboard Emulation**: High-speed, buffer-managed keystroke transmission engine.
- **8-Hour Access Window**: New users automatically receive an 8-hour trial window upon initial Google sign-in.
- **Super Admin Management**: Admin panel with access extension (e.g. +8 hours or permanent), operational notes, and account audits.
- **Service Modes**:
  - `ACTIVE`: Full operational state.
  - `MAINTENANCE`: Informative screen with administrative bypass.
  - `DISABLED`: Immediate session termination.
- **Local Storage & Offline Support**: Room Database caching for offline scripts, audit logs, and settings.
- **Web Companion & APK Distribution**: Integrated static server delivering the web dashboard and direct APK downloads.

## Project Structure

```
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt
│       │   │   ├── auth/          # Google Sign-In & Auth Management
│       │   │   ├── keyboard/      # HID Reports, Keyboard Mapper, Descriptors
│       │   │   ├── storage/       # Room DB, Script DAOs, Settings
│       │   │   ├── ui/            # Jetpack Compose Screens & Theme
│       │   │   └── TypingForegroundService.kt
│       │   └── res/               # Android Drawables, Layouts, Values
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── .build-outputs/
│   └── app-debug.apk              # Ready-to-install debug APK
├── build.gradle.kts
├── settings.gradle.kts
├── server.js                      # Web companion server & APK host
└── package.json
```

## Running the Web Companion

```bash
npm start
```
Starts the companion server on `http://localhost:3000` with the live keystroke simulator and APK download endpoint.

#!/bin/bash
set -e

echo "=== Building REPLICA Windows Companion v1.2.0 ==="
mkdir -p companion
mkdir -p public/downloads
mkdir -p app/src/main/assets/companion

# Compile with 64-bit MinGW cross-compiler
x86_64-w64-mingw32-gcc -O2 -Wall \
  companion/src/main.c \
  -o companion/replica-companion.exe \
  -lws2_32

# Verify file architecture
echo "Checking binary type:"
file companion/replica-companion.exe

# Copy to required distribution locations
cp -f companion/replica-companion.exe public/downloads/replica-companion.exe
cp -f companion/replica-companion.exe app/src/main/assets/companion/replica-companion.exe

# Calculate file size and SHA-256
FILE_SIZE=$(wc -c < companion/replica-companion.exe | tr -d ' ')
FILE_SHA=$(sha256sum companion/replica-companion.exe | awk '{print $1}')
LAST_UPDATED=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")

echo "Generated EXE size: $FILE_SIZE bytes"
echo "Generated SHA-256:  $FILE_SHA"

# Update companion_meta.json
cat > companion_meta.json <<EOF
{
  "version": "1.1.0",
  "protocolVersion": 1,
  "minAppVersion": "1.0.0",
  "fileName": "replica-companion.exe",
  "fileSize": $FILE_SIZE,
  "sha256": "$FILE_SHA",
  "uploadedBy": "nani68629@gmail.com",
  "lastUpdated": "$LAST_UPDATED",
  "releaseNotes": "Updated Windows Companion v1.1.0: automatic ADB discovery, authorization prompt diagnostics, dual-tunnel (8989 reverse + 8990 forward) auto-dial, QuickEdit freeze prevention, and multi-threaded Unicode typing."
}
EOF

echo "=== Rebuild and installation completed successfully ==="

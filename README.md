# replica_kspp - "I replicate keyboard"

A high-performance React application emulating a server-controlled Bluetooth HID keyboard and auto-typer with 8-hour access control, owner passcode security, and administrative oversight.

## Features

- **Keystroke Transmission Engine**: Full US QWERTY 101-key mapping streaming arbitrary text and code character-by-character over standard USB/Bluetooth HID reports.
- **8-Hour Access Control**: Genuine new user registrations receive an automatic 8-hour access window. Access expiration and state gates are enforced cleanly.
- **Privacy & Security**: Regular users never see administrator emails; they simply submit access requests directly to the administrative queue with a single tap.
- **Admin Control Center**:
  - Review and grant 8-hour or permanent access to new users.
  - Re-approve and restore terminated users at any time.
  - Add users and grant access directly.
  - Attach operational admin notes to user accounts.
  - System-wide Global Service Control (Active, Maintenance, Disabled).
- **Interactive Neural Sphere Illusion**: High-fidelity 3D optical illusion animation with rotating wireframe effects, holographic glowing aura, and interactive perspective tilt.
- **Owner Passcode Security**: Local salted SHA-256 passcode lock with numeric PIN keypad and instant emergency lock.
- **Saved Texts Storage**: 100% client-side local document storage with quick sample texts (Java, Shell, Text).
- **Keyboard Test Mode**: Real-time 8-byte HID report packet inspector with automated US QWERTY verification.

## Development

```bash
npm install
npm run dev
```

Build for production:

```bash
npm run build
```

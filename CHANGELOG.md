# Changelog

All notable changes to Spectre are documented here.

---

## [1.8.8] — 2026-06-25

### Added
- **Liquid Glass Navigation Bar**: iOS-like premium glassmorphic bottom bar using custom shaders, real-time vibrancy, and gyroscope tilt specular highlights (ported from KernelSU)
- **Fluid Spring Animations**: Smooth damped drag physics and interactive highlights for navigation indicator transitions
- **Android App Shortcuts**: Long-press app icon to quickly access Password Generator, Watchtower, or Send list
- **Dynamic Capsule Heights**: Automatically collapses and centers bottom navigation bar when labels are disabled

### Fixed
- **Bitwarden Send Crash**: Fixed `SymmetricKey` length crash ("must be 64 bytes") by generating correct 64-byte keys using secure random bytes rather than a 32-character hex string
- **Text Truncation**: Fixed Watchtower tab label showing as "Watchtowe" and Check Breaches action button text wrapping
- **Menu Display Name**: Replaced redundant raw email in drawer header with clean "My Vault" fallback
- **Sync Precision Mismatches**: Absorbed sub-second differences between client and server revision times to prevent false-positive sync conflicts

---

## [1.8.4] — 2026-06-22

### Added
- **Link Cleaner**: Share any tracking-laden URL to Spectre to strip UTM params, affiliate tags, and tracking identifiers from 15+ platforms (Amazon, YouTube, Reddit, Twitter/X, Bilibili, Douyin, etc.)
- **SSH Agent**: Full SSH agent integration — use vault SSH keys directly in terminal apps via a local agent socket
- **Passkeys / FIDO2**: Credential provider service for passkey creation and authentication (Android 14+)
- **WebDAV Backup**: Encrypted AES-256-GCM vault backups to any WebDAV server (Nextcloud, etc.)
- **Quick Settings Tile**: Toggle generator directly from the notification shade
- **Frosted Glass UI**: Real-time background blur on the navigation bar and menu overlay (API 31+)
- **Modern Toast Notifications**: Pill-shaped snackbars replacing the system toast

### Fixed
- **HIBP Check**: Breach check results no longer get wiped when vault state refreshes
- **HIBP API**: Added User-Agent header to prevent Cloudflare 403 blocks
- **Biometric toggle**: No longer shows as enabled on first launch when it was never set up
- **GitHub link**: View Source Code in Settings → Other now opens the correct repository

### Optimised
- Target SDK upgraded to 37
- Locale filtering strips non-English resources from all dependencies
- ABI filtering: arm64-v8a + x86_64 only (no 32-bit binaries)
- Metadata/license file exclusions from packaged APK
- Removed duplicate Lifecycle Compose dependency

---

## [1.8.3] — Initial internal release

- Core architecture: Bitwarden API sync, KeePass import, AES-256 vault encryption
- TOTP engine, clipboard auto-clear, autofill service
- Differential sync engine (3-way merge)
- Glassmorphic UI with 8 themes (Midnight, Phantom, Obsidian, Espresso, Matcha, Nord, Rose, Sky)

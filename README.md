# AG Client (Beta v1.0.0) - Standalone Minecraft Bedrock Proxy for Android

[![Android CI & Release](https://github.com/Aayaan-Ghosh/AG-Client/actions/workflows/android-build.yml/badge.svg)](https://github.com/Aayaan-Ghosh/AG-Client/actions)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen)
![Protocol](https://img.shields.io/badge/Bedrock%20Protocol-CloudburstMC-blue)
![Version](https://img.shields.io/badge/Version-1.0.0--beta-cyan)

**AG Client** is a high-performance, ad-free standalone Android APK acting as a local Man-in-the-Middle (MitM) RakNet proxy between your local Minecraft: Bedrock Edition client and any remote target Bedrock server.

---

## ⚡ Core Architectural Features

1. **Local RakNet Proxy Engine:**
   - Runs an Android `ForegroundService` with persistent ongoing notification and wake lock.
   - Binds on `127.0.0.1:19132` (or custom port).
   - Automatically broadcasts LAN discovery packets (`BedrockPong`), making the proxy appear instantly under the **"Friends" / "LAN Worlds"** tab inside Minecraft.

2. **Microsoft OAuth 2.0 Device Code Login:**
   - Full support for Microsoft OAuth 2.0 Device Code flow.
   - Generates an 8-character verification code for `https://microsoft.com/link`.
   - Exchanges tokens with Xbox Live User Authentication and XSTS relying party (`https://multiplayer.minecraft.net/`).
   - Securely caches refresh tokens in Android `EncryptedSharedPreferences`.
   - Supports offline mode self-signed identity chains when Xbox authentication is disabled.

3. **Zero Public Leak Private Chat & Command Engine:**
   - **Dot Commands (`.` prefix):** All commands typed in chat (e.g. `.killaura`, `.fly`, `.speed 2.5`, `.gui`) are intercepted, processed, and **instantly dropped** before reaching the remote server socket. Feedback is provided locally via `[Proxy]` chat packets.
   - **Slash Commands (`/` prefix):** Injects proxy command definitions into downstream `AvailableCommandsPacket` to enable native client auto-complete. Intercepts `CommandRequestPacket` and drops registered proxy commands so the remote server never sees them.

4. **Dual GUI System:**
   - **In-Game Native Bedrock GUI:** Type `/gui`, `/menu`, `.gui`, or `.menu` to open native Bedrock modal forms (`ModalFormRequestPacket` using `SimpleForm` for categories and `CustomForm` for sliders, toggles, and dropdowns). Intercepts and consumes `ModalFormResponsePacket` matching reserved form IDs (`99001`–`99099`).
   - **Out-of-Game Jetpack Compose GUI:** Sleek Material 3 dark cyber dashboard with target IP/Port configuration, Xbox login manager, start/stop service button, module dashboard, and live packet console.

5. **28+ Modules across 5 Categories:**
   - **Combat:** `/killaura`, `/reach`, `/hitbox`, `/autoclicker`, `/triggerbot`, `/tpaura`, `/autocrystal`, `/anchoraura`, `/autototem`, `/knockback`, `/backtrack`, `/shielddisabler`, `/maceswap`
   - **Movement:** `/fly`, `/speed`, `/phase`, `/jetpack`, `/boatfly`, `/noslow`, `/step`, `/waterwalk`, `/airjump`
   - **Visual:** `/xray`, `/playeresp`, `/itemesp`, `/tracers`, `/freecam`, `/chunkfinder`, `/holeesp`, `/noinvisible`
   - **World Automation:** `/scaffold`, `/nuke`, `/automine`, `/autotoolswap`, `/fastinteract`
   - **Utility:** `/gui`, `/config`, `/ghost`

---

## 🛠️ GitHub Actions Cloud CI & Compilation

AG Client is pre-configured with a single-step GitHub Actions build workflow (`.github/workflows/android-build.yml`). Every push or pull request automatically:
- Checks out the repository
- Configures JDK 17 (Temurin) and Gradle 8.5
- Runs `./gradlew assembleDebug --stacktrace`
- Uploads the debug APK as a downloadable artifact
- Automatically generates GitHub Beta Releases tagged with `v1.0.0-beta`

---

## 📱 How to Use

1. Launch **AG Client** on your Android device.
2. Enter the **Target Server IP** and **Port** (e.g., `geo.hivebedrock.network:19132`).
3. (Optional) Log in with Xbox Live using the **Login with Xbox** button for online servers.
4. Tap **START PROXY**.
5. Open **Minecraft: Bedrock Edition**, go to **Play** > **Friends**, and join the **AG Client Beta** LAN World.
6. In-game, open chat and type `.gui` or `/gui` to configure modules on the fly!

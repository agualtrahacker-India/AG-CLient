# AG Client Beta - Standalone Minecraft Bedrock Proxy for Android Implementation Plan

**Goal:** Build a complete, production-ready, ad-free Android APK named "AG Client" (version 1.0.0-beta) acting as a local Man-in-the-Middle (MitM) RakNet proxy between the Minecraft Bedrock client and remote servers, featuring 28+ modules across 5 categories, Microsoft OAuth 2.0 Device Code authentication, in-game Modal Form GUI, zero-leak private command engine, Jetpack Compose Material 3 UI, and automated GitHub Actions CI build & release workflow.

**Architecture:** 
The application runs an Android `ForegroundService` binding a local RakNet server (`0.0.0.0:19132`) broadcasting LAN `BedrockPong` discovery. A bidirectional proxy pipeline bridges the local client session with the target remote server session, routing all incoming and outgoing packets through an extensible `ModuleManager` and `WorldStateTracker`. Private chat dot-commands (`.`) and slash-commands (`/`) are intercepted and dropped before reaching the server socket, with an in-game Bedrock Modal Form GUI system (`ModalFormRequestPacket`) and an out-of-game Jetpack Compose Material 3 dashboard.

**Tech Stack:**
- Kotlin, Android SDK (minSdk 26, targetSdk 34, compileSdk 34)
- CloudburstMC Protocol (`bedrock-connection`, `bedrock-codec`, `common`)
- Jetpack Compose, Material 3, AndroidX Lifecycle, EncryptedSharedPreferences
- Kotlinx Coroutines, Google Gson
- GitHub Actions CI (JDK 17, Gradle 8.5)

**Spec:** `D:\Aayaan Ghosh\Minecraft Bedrock Android Proxy - Antigravity CLI Master Specification.pdf`

## Global Constraints
- App Name: "AG Client"
- Version: "1.0.0-beta" (versionCode: 1)
- Package Name: `com.aayaan.agclient`
- Strict Stack Adherence: Kotlin + CloudburstMC Protocol for single-step Gradle compilation on GitHub Actions
- No Invented Packets: Use official packet classes from `org.cloudburstmc.protocol.bedrock.packet`
- Protocol Reality: RakNet over UDP (default 19132), ECDH (secp384r1) + AES-256-GCM/CFB8, Zlib/Snappy compression
- Zero Public Leak Rule: Any command starting with `/` or `.` belonging to the proxy MUST be dropped before reaching the remote server socket
- Before running any git/github CLI command, explicitly inform and ask the user for confirmation

---

### Task 1: Project Scaffolding, Gradle DSL & Cloud CI
- Create `settings.gradle.kts` with OpenCollab repositories
- Create root `build.gradle.kts` and `app/build.gradle.kts` with all dependencies
- Create `gradle.properties` and Gradle wrapper configuration
- Create `.github/workflows/android-build.yml` with JDK 17, Gradle 8.5, APK artifact upload, and GitHub Release automation
- Create `AndroidManifest.xml` with required foreground service and network permissions
- Create resource files (strings, colors, theme, vector drawables)

### Task 2: Core RakNet Proxy & Microsoft OAuth Auth Engine
- Create `XboxAuthManager.kt` implementing Microsoft OAuth 2.0 Device Code flow, Xbox Live User token exchange, XSTS relying party authorization, and EncryptedSharedPreferences caching
- Create `BedrockProxyService.kt` Android ForegroundService with ongoing notification and LAN BedrockPong broadcasting
- Create `ProxySession.kt` bridging `BedrockServerSession` and `BedrockClientSession`, handling ECDH handshakes, client data extraction (skins, geometry, persona), and bidirectional packet routing

### Task 3: Command Router, Private Chat Filter & In-Game Bedrock Modal Form GUI
- Create `CommandManager.kt` intercepting TextPacket (`.`) and CommandRequestPacket (`/`), guaranteeing zero remote server leak
- Inject custom proxy commands into downstream `AvailableCommandsPacket` for native client autocompletion
- Create `InGameGuiManager.kt` generating Bedrock SimpleForm and CustomForm JSON payloads (`ModalFormRequestPacket`) and intercepting `ModalFormResponsePacket`

### Task 4: World State Tracker & Entity / Chunk Cache
- Create `WorldStateTracker.kt` tracking player position, rotation, motion, health, and inventory
- Create `EntityTracker.kt` caching nearby entities, bounding boxes, and metadata
- Create `ChunkCache.kt` caching chunk palettes and block updates for fast spatial lookups

### Task 5: Module Manager & 28+ Modules Implementation
- Implement base `Module.kt`, `ModuleProperty.kt`, `ModuleCategory.kt`, and `ModuleManager.kt`
- Category 1: Combat Modules (KillAura, Reach, Hitbox, AutoClicker, TriggerBot, TpAura, AutoCrystal, AnchorAura, AutoTotem, Knockback, Backtrack, ShieldDisabler, MaceSwap)
- Category 2: Movement Modules (Fly, Speed, Phase, Jetpack, BoatFly, NoSlow, Step, WaterWalk, AirJump)
- Category 3: Visual & ESP Modules (Xray, PlayerESP, ItemESP, Tracers, Freecam, ChunkFinder, HoleESP, NoInvisible)
- Category 4: World Automation Modules (Scaffold, Nuke, AutoMine, AutoToolSwap, FastInteract)
- Category 5: Utility Modules (Gui, Config, Ghost)
- Implement `ConfigManager.kt` for JSON serialization/deserialization

### Task 6: Native Android GUI (Jetpack Compose / Material 3) & Documentation
- Build `MainActivity.kt` with modern Material 3 cyber theme
- Implement Server Config Tab, Xbox Auth Card, Proxy Service Controls, Live Packet Console, and Module Dashboard
- Create `README.md` and complete documentation

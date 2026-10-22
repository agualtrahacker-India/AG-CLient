# 🛡️ AG Client - Standalone Minecraft Bedrock Proxy for Android

[![Android CI & Release](https://github.com/agualtrahacker-India/AG-CLient/actions/workflows/android-build.yml/badge.svg)](https://github.com/agualtrahacker-India/AG-CLient/actions)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen)
![Target SDK](https://img.shields.io/badge/Target%20SDK-Android%2014%20(API%2034)-blue)
![Protocol](https://img.shields.io/badge/Bedrock%20Protocol-CloudburstMC-purple)
![Commands & Modules](https://img.shields.io/badge/Commands%20%26%20Modules-70%20Total-neon)
![License](https://img.shields.io/badge/License-GPL--3.0-orange)

**AG Client** is a high-performance, ad-free standalone Android application acting as a local **Man-in-the-Middle (MitM) RakNet Proxy** between your local Minecraft: Bedrock Edition app and any remote target Bedrock server (such as The Hive, NetherGames, CubeCraft, or custom servers/realms).

---

## 📚 Libraries & Technical Stack

AG Client is built with modern, battle-tested Android and network protocol libraries:

| Library / Framework | Version | Purpose |
| :--- | :--- | :--- |
| **CloudburstMC `bedrock-connection`** | `3.0.0.Beta14-SNAPSHOT` | Manages Bedrock client & server network sessions, RakNet framing, packet compression, and handshake pipelines. |
| **CloudburstMC `bedrock-codec`** | `3.0.0.Beta14-SNAPSHOT` | Comprehensive Minecraft: Bedrock Edition packet serialization, deserialization, and multi-version protocol codecs. |
| **CloudburstMC `common`** | `3.0.0.Beta14-SNAPSHOT` | Shared protocol utilities, buffer management, and packet handling abstractions. |
| **CloudburstMC `math`** | `2.0` | Immutable fast vector and rotation mathematics for 3D trajectory and hitbox calculations. |
| **Netty (`io.netty`)** | `4.1.101.Final` | High-performance asynchronous event-driven network engine (`transport`, `codec`, `buffer`, `common`) for ultra-low latency UDP packet dispatching. |
| **Jetpack Compose & Material 3** | `2024.02.00 BOM` | Modern Android declarative UI framework powering the sleek cyber-themed dashboard. |
| **Kotlin Coroutines & Flow** | `1.7.3` | Asynchronous non-blocking background task orchestration and real-time state sharing (`StateFlow`, `SharedFlow`). |
| **Google Gson** | `2.10.1` | JSON serialization and deserialization for module configuration presets and JWT claims. |
| **AndroidX Security Crypto** | `1.1.0-alpha06` | Hardware-backed `EncryptedSharedPreferences` for secure offline identity caching. |
| **AndroidX Core & Lifecycle** | `1.12.0` / `2.7.0` | Modern Android foreground service lifecycle management, notification channels, and permissions. |

---

## 🚀 Complete User Guide

### 1. Installation
1. Download the latest `app-debug.apk` from the **Releases** tab.
2. Open the APK on your Android device and tap **Install**.
3. *(Play Protect scans the app automatically and verifies it is 100% clean and safe).*

### 2. Configure Your Target Server
1. Launch **AG Client**.
2. Under **Server Configuration**:
   - **Target Server IP:** Set the destination server (default: `geo.hivebedrock.network`).
   - **Target Server Port:** Set the port (default: `19132`).
   - **Local Proxy Port:** Set the local bind port (default: `19132`).

### 3. Start the Proxy
1. Tap the cyan **START PROXY** button.
2. AG Client starts an Android Foreground Service with a persistent status notification and acquires a Wi-Fi MulticastLock.
3. The dashboard status indicator turns green: **RUNNING**.

### 4. Connect from Minecraft Bedrock
You have two ways to connect from your phone:

#### Method A: The "Servers" Tab (Recommended & 1-Time Setup)
1. Open **Minecraft: Bedrock Edition** on your phone.
2. Tap **Play** $\rightarrow$ Tap the **Servers** tab.
3. Scroll down to the bottom and tap **Add Server**.
4. Fill in:
   - **Server Name:** `AG Hive` (or any name you choose)
   - **Server Address:** `127.0.0.1`
   - **Port:** `19132`
5. Tap **Save**.
6. Tap the server from your list and hit **Join**! Minecraft immediately connects to AG Client on loopback, and AG Client bridges you directly into The Hive!

#### Method B: The "Friends" Tab (LAN Discovery)
1. In Minecraft, tap **Play** $\rightarrow$ Tap the **Friends** tab.
2. Scroll down to the **LAN Games** section.
3. Look for **AG Client Beta [MITM Proxy]** and tap it to connect.

### 5. In-Game Controls & GUI
Once you are connected to the world:
- Open chat and type `.menu` or `/menu` (or `.gui` / `/gui`) to open the **in-game Bedrock Modal Form GUI**.
- Toggle modules, adjust sliders, and switch presets right from your screen!
- Type `.help` to see all available commands.

---

## 🔒 Native Profile Passthrough (Zero Extra Login)

AG Client features **Native Minecraft Passthrough**:
* You **never need to log into Microsoft twice** or enter browser device codes.
* Minecraft on your phone is already authenticated with your personal Microsoft account.
* When you join `127.0.0.1:19132`, the proxy automatically captures your authentic, signed Xbox Live identity tokens, Gamertag, and custom skin from Minecraft and forwards them directly to the remote server.
* Remote servers see your **real player name, real skin, and genuine account**!

---

## 🤐 Zero Chat Leak Engine

AG Client implements a strict dual-layer private chat filter:
* **Dot Commands (`.` prefix):** All commands typed in chat (e.g. `.killaura`, `.fly`, `.help`) are intercepted and **instantly dropped** by the proxy before reaching the server. The server and other players never see what you type.
* **Slash Commands (`/` prefix):** Injects proxy command definitions into Bedrock's downstream `AvailableCommandsPacket` to provide native in-game autocompletion, while blocking `CommandRequestPacket` from reaching the remote server.
* Feedback and toggle alerts are delivered locally to your chat or actionbar via private `TextPacket` injections.

---

## 📋 Master Reference: All 70 Modules & Commands

AG Client includes a comprehensive suite of **70 modules and commands** divided into 4 specialized categories:

### 1. Core & Utility Commands (9 Commands)
| # | Command / Module | Function & Operational Summary |
| :-: | :--- | :--- |
| **1** | `.help` | Displays the master list of commands and active modules directly in local chat. |
| **2** | `.menu` (or `.gui`) | Opens the native Bedrock ClickGUI / Modal Form overlay on your screen. |
| **3** | `.bind <module> <key>` | Assigns an activation game action or button alias to a module. |
| **4** | `.config <save\|load> <name>` | Saves or loads module presets to and from local JSON storage. |
| **5** | `.disableall` | Immediately toggles off all currently running client modules. |
| **6** | `.ghost` | Suppresses proxy notifications and commands from game chat for streaming or recording. |
| **7** | `.relog` | Disconnects and immediately reconnects the proxy session to the host server. |
| **8** | `.panic` | Emergency shutdown command that disables all modifications and wipes active binds. |
| **9** | `.coords` | Displays precise sub-block coordinates and direction vectors. |

---

### 2. Combat Modules & Commands (29 Commands)
| # | Command / Module | Function & Operational Summary |
| :-: | :--- | :--- |
| **10** | `/reach` | Configures extended melee reach distance. |
| **11** | `/killaura` | Automatically scans, paths, and strikes valid entities within range. |
| **12** | `/tpaura` | Exploits packet offsets to teleport directly to targets and attack. |
| **13** | `/hitbox` | Expands enemy collision and interaction bounds for easier targeting. |
| **14** | `/knockback` | Cancels or dampens incoming horizontal/vertical knockback packets (`SetEntityMotionPacket`). |
| **15** | `/knockbackdelay` | Alters incoming knockback packet timing to manipulate hit trade sequences. |
| **16** | `/backtrack` | Delays entity positional sync packets to hit opponents at past locations. |
| **17** | `/triggerbot` | Automatically executes attacks when the crosshair intersects target collision. |
| **18** | `/autoclicker` | Injects configurable automated left-clicks per second (CPS). |
| **19** | `/afkclicker` | Maintains continuous automated attack or item-use actions while idle. |
| **20** | `/doubleclick` | Injects extra attack packets per physical swing. |
| **21** | `/autototem` | Scans inventory and swaps a Totem of Undying to the offhand in under 5ms. |
| **22** | `/autopotion` | Detects health threshold drops and automatically throws splash potions downwards. |
| **23** | `/autocrystal` | Handles end crystal placement and immediate break detonation logic. |
| **24** | `/anticrystal` | Places blast-resistant shielding blocks to block crystal explosion vectors. |
| **25** | `/autocart` | Automates placement and rapid ignition of TNT minecarts near targets. |
| **26** | `/autoweb` | Drops cobwebs at opponent coordinates to restrict enemy movement. |
| **27** | `/autojumpreset` | Executes calculated jumps upon taking damage to counter momentum. |
| **28** | `/autocrit` | Synchronizes attack timing strictly with downward falling motion for critical hits. |
| **29** | `/autoswitch` | Automatically alternates between designated hotbar slots during combat. |
| **30** | `/maceswap` | Detects falling state, equips a mace for damage calculation, and swaps back. |
| **31** | `/macedmg` | Applies automated fall-momentum amplification to mace swing packets. |
| **32** | `/shielddisabler` | Swaps dynamically to an axe when striking a blocking enemy shield. |
| **33** | `/aimassist` | Smoothly shifts crosshair yaw and pitch toward the nearest enemy. |
| **34** | `/autolog` | Automatically severs server connection when health reaches a defined threshold. |
| **35** | `/anchoraura` | Automates placement, glowstone charging, and detonation of respawn anchors. |
| **36** | `/multiaura` | Distributes attack packets across multiple targets simultaneously. |
| **37** | `/infiniteaura` | Dispatches pathfinding packets to attack targets across extreme distances. |
| **38** | `/projectileaimbot` | Calculates trajectory, gravity, and velocity leading for ranged weapons. |

---

### 3. Movement Modules & Commands (18 Commands)
| # | Command / Module | Function & Operational Summary |
| :-: | :--- | :--- |
| **39** | `/fly` | Overrides server motion packets for custom creative-style flight. |
| **40** | `/boatfly` | Enables directional flight while operating a boat entity. |
| **41** | `/speed` | Multiplies horizontal movement velocity vectors. |
| **42** | `/noslow` | Eliminates movement penalties during eating, drinking, or bow charging. |
| **43** | `/airjump` | Resets the ground state mid-air to allow continuous jump cycles. |
| **44** | `/phase` | Exploits clip mechanics to pass through targeted solid blocks. |
| **45** | `/jetpack` | Generates upward vertical velocity while holding the jump control. |
| **46** | `/jumpboost` | Client-side modification of jump height mechanics. |
| **47** | `/levitate` | Emulates levitation effect packets for gradual vertical ascent. |
| **48** | `/slowfalling` | Clamps maximum negative vertical velocity to simulate slow falling. |
| **49** | `/taptp` | Teleports position vectors directly to the block face tapped on-screen. |
| **50** | `/motionfly` | Smooth momentum-based flight using velocity packet injection. |
| **51** | `/autorocket` | Automatically uses firework rockets at set intervals during elytra glide. |
| **52** | `/autoelytra` | Automatically deploys elytra flight upon entering airborne fall states. |
| **53** | `/bunnyhop` | Maintains optimal sprint-jump chaining to preserve peak velocity. |
| **54** | `/step` | Automatically increases step height to traverse full blocks without jumping. |
| **55** | `/waterwalk` | Alters fluid collision to treat water surfaces as solid ground. |
| **56** | `/glide` | Counteracts gravity deceleration to glide forward over distances. |

---

### 4. Visual, World & Render Modules (14 Commands)
| # | Command / Module | Function & Operational Summary |
| :-: | :--- | :--- |
| **57** | `/freelook` | Uncouples the rendering camera from player character orientation. |
| **58** | `/freecam` | Detaches the spectator camera from the player model's physical hitbox. |
| **59** | `/arraylist` | Renders a real-time HUD actionbar overlay of all currently enabled modules. |
| **60** | `/armorhud` | Displays current armor condition and durability metrics on the HUD. |
| **61** | `/inventoryhud` | Projects inventory slots and item counts onto the active HUD. |
| **62** | `/targethud` | Displays current opponent metrics (health, armor, distance) above hotbar. |
| **63** | `/xray` | Injects client-side ghost block updates to reveal hidden ores and stashes. |
| **64** | `/tracers` | Draws geometric particle lines directly to nearby player entities. |
| **65** | `/blocktracers` | Renders particle lines pointing toward specific high-value blocks. |
| **66** | `/holeesp` | Highlights bedrock and obsidian shafts safe from blast vectors. |
| **67** | `/schematic` | Renders holographic blueprint layers directly into the local environment. |
| **68** | `/chunkfinder` | Scans incoming chunk packets for non-natural block clusters indicating bases. |
| **69** | `/suschunkfinder` | Flags anomalous chunk states, player activity indicators, and hidden stashes. |
| **70** | `/waypoints` | Renders directional beacons and distance calculations for saved map locations. |

---

## 🔄 Built-in 1-Tap Auto-Updater

AG Client includes a built-in background auto-updater:
1. Whenever a new release is pushed to GitHub, the app checks the GitHub API on launch.
2. An **"Update Available"** card appears on the Dashboard with release notes.
3. Tap **Download Update** $\rightarrow$ Tap **Install** to update the app directly on your phone without ever visiting a browser.

---

## ⚖️ Disclaimer

AG Client is developed as an educational tool and advanced network proxy research project for Minecraft: Bedrock Edition protocol reverse-engineering. Always adhere to the terms of service and rules of the specific servers you connect to.

---

## 📜 Author & License

* **Lead Developer & Creator:** **Aayaan Ghosh** ([@agualtrahacker-India](https://github.com/agualtrahacker-India))
* **License:** [GNU General Public License v3.0 (GPL-3.0)](LICENSE)

### What this license guarantees:
1. **Permanent Author Credit:** You are credited as the creator and original author of this project. Any forks or copies must retain this copyright notice.
2. **Full Author Control:** As the copyright holder, you maintain 100% control over the repository, its releases, features, and official builds.
3. **Copyleft Protection (Always Free & Open-Source):** Anyone who uses, modifies, or forks this code **must** keep their version open-source and free under GPL-3.0. No corporation or third party can legally steal your code to make it proprietary or closed-source.

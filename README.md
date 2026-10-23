# 🛡️ AG Client (v2.9.6) - Standalone Minecraft Bedrock Proxy for Android

[![Android CI & Release](https://github.com/agualtrahacker-India/AG-CLient/actions/workflows/android-build.yml/badge.svg)](https://github.com/agualtrahacker-India/AG-CLient/actions)
![Version](https://img.shields.io/badge/Release-v2.9.0--beta.1-blueviolet)
![Branch](https://img.shields.io/badge/Branch-connection--test-orange)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen)
![Target SDK](https://img.shields.io/badge/Target%20SDK-Android%2014%20(API%2034)-blue)
![Bedrock Protocol](https://img.shields.io/badge/Bedrock%20Protocol-CloudburstMC%201.21%2B-purple)
![Commands & Modules](https://img.shields.io/badge/Commands%20%26%20Modules-70%20Total-neon)
![License](https://img.shields.io/badge/License-GPL--3.0-orange)

**AG Client** is a high-performance, ad-free standalone Android application acting as a local **Man-in-the-Middle (MitM) RakNet Proxy** between your local Minecraft: Bedrock Edition app and any remote target Bedrock server (such as The Hive, NetherGames, CubeCraft, or custom servers/realms).

**Creator & Lead Developer:** **Aayaan Ghosh** ([@agualtrahacker-India](https://github.com/agualtrahacker-India))

---

## ✨ What's New in v2.9.6 (Featured Server Auth & Dynamic Protocol Support)

* 🔬 **Dedicated `connection-test` Branch & Beta 1 Diagnostics Build**: Isolated test environment to definitively determine whether network connection issues are within client code or Android runtime/environment.
* 🛰️ **RakNet Packet Inspector & Live Hex Telemetry**: Real-time packet parsing (`0x05`, `0x06`, `0x07`, `0x08`, `0x09`, `0x10`, `0x13`, `0x19`, `0x1c`) for every UDP datagram passing between Minecraft, tun0, and the proxy relay.
* 🧪 **Integrated In-App Connectivity Test Suite**: "Run Network Diagnostic" probe directly in the Console testing port 19133 responsiveness, DNS latency, and upstream UDP ping reachability.
* 📋 **Interactive Feedback & Comprehensive Diagnostic Bundle**: Record user response ("Working", "Stuck: External Server", "Stuck: Multiplayer", "Timed Out") and copy a complete diagnostic bundle for instant investigation.
* ⚡ **Unlimited Rate-Limit Free Auto-Updater**: Uses GitHub Web Redirects and Fastly CDN `version.json` caching so anyone can check for updates an unlimited number of times without hitting GitHub API 403 limits.
* 🧵 **Non-blocking Upstream Coroutine Dispatcher**: Upstream DNS resolution and connection initialization execute off Netty's NIO worker thread to eliminate thread stalling.
* 💬 **In-Game Join Welcome & Attribution**: When spawning on any server, a private message is sent to your chat:
  ```text
  ----------------------------------------
  AG Client v2.9.6 - Made by Aayaan Ghosh
  Connected to server successfully!
  Type .help or /help to see command lists.
  ----------------------------------------
  ```
* 📜 **Lunar Proxy Style Chat Commands**: Running `.help`, `/help`, `.cmds`, or `/commands` in chat prints all 70 commands grouped by category.

---

## 🚀 Complete Quick Start & Connection Guide

### 1. Installation
1. Download the latest **`AG-Client-v2.9.6-release.apk`** from [Releases](https://github.com/agualtrahacker-India/AG-CLient/releases).
2. Install the APK on your Android device (Play Protect verifies it is clean and safe).

---

### 2. How to Connect in Minecraft (Choose Method A or B)

#### 🟢 Method A: Virtual Sandbox Router (Recommended for Mobile Data / SIM Net & Wi-Fi)
1. Open **AG Client**.
2. In **Server Configuration**, set your destination server (e.g., `geo.hivebedrock.network` or `play.nethergames.org` on port `19132`).
3. Tap **"START SANDBOX"** (Android will ask for a 1-time local network permission — tap OK).
4. Tap **"Copy 10.0.0.1"**.
5. Tap **"Launch Minecraft"** (or open Minecraft):
   - Tap **Play**.
   - Tap **Servers** (the "Servers" tab at the top or stacked cubes icon 🖥️ in Ore UI).
   - Scroll down to the bottom and tap **"Add Server"**.
   - **Server Name:** `AG Client`
   - **Server Address:** `10.0.0.1`
   - **Port:** `19133`
   - Tap **Save & Join**!

> [!NOTE]
> The Virtual Sandbox is a **100% local virtual adapter** running inside your phone's memory. No traffic is sent to external VPN servers. It prevents mobile carrier networks (Jio, Airtel, etc.) from dropping loopback packets.

---

#### 🔵 Method B: Local Loopback Mode (Direct 127.0.0.1)
1. In Minecraft: Open **Settings** ➡️ **General** (or **Profile**) ➡️ Toggle **"Use Cellular Data"** to **ON** (required by Minecraft on mobile SIM data).
2. Open **AG Client** ➡️ Set your Target Server IP & Port ➡️ Tap **"START PROXY"**.
3. In Minecraft:
   - Tap **Play** ➡️ **Servers** ➡️ **Add Server**.
   - **Server Address:** `127.0.0.1`
   - **Port:** `19133`
   - Tap **Save & Join**!

---

## 🎮 In-Game Controls & Commands

Once connected to any server:

* **Open ClickGUI / Menu:** Type `.menu` or `/menu` in chat to open the interactive Bedrock Modal Form GUI to toggle modules and adjust sliders.
* **Master Command List:** Type `.help` or `/help` in chat to view all 70 commands directly in chat text.
* **Prefixes:** Any command can be run using either `.` (dot) or `/` (slash).
* **Zero Chat Leak:** All commands are intercepted locally by AG Client and dropped before reaching the remote server. Other players and server staff will never see what you type.

---

## 📋 Master Reference: All 70 Modules & Commands

### 1. Core & Utility Commands (9 Commands)
| # | Command / Module | Function & Operational Summary |
| :-: | :--- | :--- |
| **1** | `.help` (or `/help`) | Displays master list of commands categorized with author credits in chat. |
| **2** | `.menu` (or `.gui`) | Opens native Bedrock ClickGUI / Modal Form overlay on your screen. |
| **3** | `.bind <module> <key>` | Assigns an activation game action or button alias to a module. |
| **4** | `.config <save\|load> <name>` | Saves or loads module presets to and from local JSON storage. |
| **5** | `.disableall` | Immediately toggles off all currently running client modules. |
| **6** | `.ghost` | Suppresses proxy notifications and commands from game chat for streaming/recording. |
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
| **17** | `/triggerbot` | Automatically executes attacks when crosshair intersects target collision. |
| **18** | `/autoclicker` | Injects configurable automated left-clicks per second (CPS). |
| **19** | `/afkclicker` | Maintains continuous automated attack or item-use actions while idle. |
| **20** | `/doubleclick` | Injects extra attack packets per physical swing. |
| **21** | `/autototem` | Scans inventory and swaps a Totem of Undying to offhand in under 5ms. |
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
| **34** | `/autolog` | Automatically severs server connection when health reaches defined threshold. |
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
| **43** | `/airjump` | Resets ground state mid-air to allow continuous jump cycles. |
| **44** | `/phase` | Exploits clip mechanics to pass through targeted solid blocks. |
| **45** | `/jetpack` | Generates upward vertical velocity while holding jump control. |
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
| **57** | `/freelook` | Uncouples rendering camera from player character orientation. |
| **58** | `/freecam` | Detaches spectator camera from player model's physical hitbox. |
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

## 🔒 Native Profile Passthrough (Zero Extra Login)

AG Client features **Native Minecraft Passthrough**:
* You **never need to log into Microsoft twice** or enter browser device codes.
* Minecraft on your phone is already authenticated with your personal Microsoft account.
* When you join `10.0.0.1:19133` (or `127.0.0.1:19133`), the proxy automatically captures your authentic, signed Xbox Live identity tokens, Gamertag, and custom skin from Minecraft and forwards them directly to the remote server.
* Remote servers see your **real player name, real skin, and genuine account**!

---

## 🔄 Built-in 1-Tap Auto-Updater

AG Client includes a built-in background auto-updater:
1. Whenever a new release is pushed to GitHub, the app checks the GitHub API on launch.
2. An **"Update Available"** card appears on the Dashboard with release notes.
3. Tap **Download Update** ➡️ Tap **Install** to update the app directly on your phone without ever opening a web browser.

---

## 📚 Technical Stack

| Component | Library / Framework | Purpose |
| :--- | :--- | :--- |
| **Network Core** | CloudburstMC `bedrock-connection` & Netty 4.1 | RakNet UDP session framing, compression, encryption, and low-latency proxy forwarding. |
| **Bedrock Codec** | CloudburstMC `bedrock-codec` | Minecraft: Bedrock Edition packet serialization and protocol handling for 1.21+. |
| **Virtual Routing** | Android `VpnService` TUN Driver | Local sandbox packet interceptor (`10.0.0.2` $\leftrightarrow$ `10.0.0.1`) bypassing mobile carrier loopback blocks. |
| **UI Framework** | Jetpack Compose & Material 3 | Modern Android declarative UI with dark neon cyber-gaming theme. |
| **Concurrency** | Kotlin Coroutines & StateFlow | Non-blocking asynchronous I/O and reactive UI state sharing. |
| **Security** | AndroidX Crypto (`EncryptedSharedPreferences`) | Hardware-backed keystore encryption for persistent configs. |

---

## 📜 Author & License

* **Lead Developer & Creator:** **Aayaan Ghosh** ([@agualtrahacker-India](https://github.com/agualtrahacker-India))
* **License:** [GNU General Public License v3.0 (GPL-3.0)](LICENSE)

### License Protections:
1. **Permanent Author Credit:** Aayaan Ghosh is credited as the creator and original author of this project. Any forks or distributions must retain this attribution.
2. **Copyleft Protection (Always Free & Open-Source):** Anyone who uses, modifies, or forks this code must keep their changes completely open-source and free under GPL-3.0. No corporation or third party may legally monetize or make this software closed-source.

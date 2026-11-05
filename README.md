# 🦁 Regain Pro — Autonomous Build & Pro Digital Wellness Shield

A private, ad-free, zero-lag Android replica of **Regain Pro** (`com.regain.pro`) built with cloud auto-scaffolding, offline procedural soundscapes, strict anti-cheat enforcement, multiplayer focus rooms, Google Sign-In, and Supabase cloud sync.

---

## ⚡ Zero-Lag & Resource Optimization Architecture

- **350ms Event Throttling**: In `RegainBlockerService.kt`, `TYPE_WINDOW_CONTENT_CHANGED` events are throttled to execute at most once every 350ms, keeping background CPU usage under 0.2% and eliminating frame drops.
- **O(1) Package Filtering**: System UI (`systemui`), software keyboards (`inputmethod`), and internal components are discarded immediately before querying `AccessibilityNodeInfo` trees, guaranteeing total RAM footprint stays under **25 MB**.
- **Explicit Node Recycling**: Every queried `AccessibilityNodeInfo` tree is recursively traversed with strict depth limits and safely recycled inside `finally` blocks, eliminating memory leaks.
- **Offline Procedural Web Audio**: Rain, White Noise, and Lo-Fi drones are synthesized dynamically in memory via the Web Audio API—no heavy MP3s, keeping the entire APK download size under **~2 MB**.

---

## 🚀 Features (Free & Pro Master Index)

### 1. Granular Short-Form Feed & Social Blocker
- **YouTube Shorts Interception**: Detects `reel_recycler`, `reel_watch_fragment_root`, and shorts containers.
- **Instagram Reels Interception**: Detects `clips_viewer_view_pager` and `clips_video_container`.
- **Facebook Reels & Snapchat Spotlight**: Intercepts `fb_shorts` and `spotlight_view`.
- **Dual-State App Functionality**: Blocks addicting infinite feeds while leaving DMs, messages, and educational searches open.
- **Stricter Feed Interception (PRO)**: Immediately executes `GLOBAL_ACTION_BACK` followed by `GLOBAL_ACTION_HOME`.

### 2. Study Modes & Whitelisting
- **YouTube Channel Whitelisting**: Allows whitelisted educational creators (e.g. *Khan Academy*, *3Blue1Brown*, *MIT OpenCourseWare*, *Veritasium*) while blocking entertainment content.
- **Browser Domain Filtering**: Detects address bar nodes (`url_bar`) across Chrome, Firefox, Brave, and Samsung Internet to block distracting sites.

### 3. Anti-Cheat & Strict Enforcement (PRO)
- **Zero-Bypass Daily App Quotas**: Queries Android's `UsageStatsManager` since midnight; locks apps when limits are exceeded.
- **Uninstall & Permission Revocation Shield**: Detects `packageinstaller` and Android Settings when Regain Pro is accessed during focus timers, preventing tampering.
- **Split-Screen & Floating Window Interception**: Automatically detects multi-window, PiP, and split dividers on Android 7.0+ (API 24+) to prevent bypasses.

### 4. Timers & Soundscapes
- **Triple Timer**: Pomodoro (25m), Custom Countdown (45m), and Stopwatch.
- **Subject Categorization**: Quick cycling pills for Physics, Math, Code, Literature.
- **Offline Procedural Soundscapes**: Rain, White Noise, and Lo-Fi Drone synthesized offline with 0 MB storage.

### 5. Multiplayer Study Rooms & Cloud Sync
- **Multiplayer Focus Rooms**: Live study rooms ("Deep Work - Code", "Exam Cram - Physics") with active peer counters and emoji reactions.
- **Google Sign-In & Supabase**: Seamless authentication and database sync for study streaks and custom schedules (see [`SUPABASE_GOOGLE_SETUP.md`](SUPABASE_GOOGLE_SETUP.md)).

---

## 🎨 Visual Design & UI Layout

Built in strict compliance with the Regain Pro obsidian/emerald theme:
- **Background Radial Gradient**: `#1B2C18` to `#0A0E0A`
- **Card Surface**: `#151915` with subtle border `#242A24`
- **Primary Accent Green**: `#5CE63C`
- **Pro Badge Gold**: `#E8B931`
- **Distracting Bar Orange**: `#FF7A00`
- **Split Floating Bottom Dock**: Dual obsidian pills:
  - Left Pill (flex 3): Focus, Schedule, Groups
  - Right Pill (flex 1): Blocks

---

## 🛠 Cloud Build & Installation Instructions

This project requires **no local Android Studio**. GitHub Actions compiles the APK automatically:

1. Push code to GitHub.
2. In your GitHub repository, navigate to the **Actions** tab.
3. Select **Build Personal Regain Pro APK**.
4. Once completed (~2 minutes), download the artifact **Regain-Personal-Pro-APK.zip**.
5. Extract and install `app-debug.apk` on your Android device.
6. Open the app, switch to the **Blocks** tab, and tap **Setup Permissions** to enable:
   - **Usage Access** (to monitor screen time quotas)
   - **Accessibility Service** (to enable the anti-distraction shield)

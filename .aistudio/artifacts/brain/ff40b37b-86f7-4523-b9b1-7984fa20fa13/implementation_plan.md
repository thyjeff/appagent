# FocusPilot Agent Core Access, 7-Day Timetable & Distraction Enforcement

An end-to-end upgrade transforming FocusPilot into an autonomous, agent-commanded student companion. Connects AI agents (Hermes, Claude Desktop, Cursor) with full device telemetry, strict distraction blocking overlays, loud DND-bypassing alarm streams, distinct 7-day schedule batch syncing, and campus location intelligence.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were aligned via interactive clarification and govern the implementation:

- **Confirmed Decision 1 (Focus Enforcement)**: Full-screen focus overlay that blocks user interaction and distracting apps during scheduled study blocks and deep work sprints.
- **Confirmed Decision 2 (Schedule Structure)**: Distinct Monday through Sunday schedules (`dayOfWeek: 1..7`) with a 7-day batch sync tool (`sync_weekly_timetable`) to fix the issue where changing one day copied over the entire week and month.
- **Confirmed Decision 3 (Agent Capture Capabilities)**: Instant silent photo capture and audio recording via background Android services without UI interruption or viewfinder popup.

---

### 1. Overview & Core Concept

FocusPilot enables an external AI agent (like Hermes running on a Raspberry Pi, Claude Desktop, or cloud agent) to act as a proctor, mentor, and accountability coach for a college student (e.g. Karunya University student). 

Through the **Common MCP Link** and **Phone Bearer Token**, the agent gains:
1. **Full Deep Device Telemetry**: Real-time battery levels, volume levels, connected Wi-Fi SSID/BSSID (campus vs. hostel context), screen time with package-level duration breakdown (e.g., YouTube Shorts, Instagram, games).
2. **Strict Distraction Enforcement**: An emergency focus overlay (`enable_strict_focus_mode`) that covers distracting apps with an inescapable full-screen focus canvas when a study session starts.
3. **Emergency Wake-Up Alarm**: RingtoneManager playback routed through `AudioAttributes.USAGE_ALARM` / `STREAM_ALARM`, ensuring the alarm rings at max volume even if the phone is on silent, vibrate, or Do Not Disturb (DND).
4. **7-Day Distinct Schedule Sync**: Separate Monday-Sunday schedule storage in Room DB and MCP, allowing full-week batch updates via `sync_weekly_timetable`.
5. **Auditory & Visual Evidence Verification**: Silent photo snapshot and short audio recording triggers for agent-driven environment verification.
6. **Campus Context & Geofencing**: Automatic detection of campus Wi-Fi vs hostel Wi-Fi, alerting the agent if the student is in the hostel during lecture hours.

---

### 2. User Experience & Visual Design

#### A. Distinct 7-Day Schedule Screen
- **Weekday Pill Selector**: Mon, Tue, Wed, Thu, Fri, Sat, Sun tabs at the top. Selecting a tab shows *only* that day's scheduled classes, labs, workouts, and study blocks.
- **Day-Specific Editing**: Adding or modifying a block on Tuesday only affects Tuesday.
- **Batch Sync Visual Indicator**: A banner showing "Synced with Hermes Weekly Schedule" with timestamp and block count.

#### B. Full-Screen Focus Overlay Screen
- When `enable_strict_focus_mode` is triggered by Hermes or scheduled deep work, an inescapable full-screen overlay opens:
  - Deep dark background with high-contrast amber/emerald typography.
  - Active study block title, elapsed time, and countdown.
  - "Deep Work Block Active — Non-essential apps locked by Hermes."
  - Emergency unlock button with a 15-second friction hold and reason entry.

#### C. Live Screen Time & Telemetry Card
- Live screen-time card displays:
  - Total screen time today (e.g. `2h 45m`).
  - Top 5 app breakdown with app name, package, and bar meter (e.g. YouTube: 45m, Instagram: 35m).
  - Quick refresh button to trigger instantaneous usage re-computation.

#### D. Streamlined Settings Screen
- Removed unused legacy toggles.
- Prominent **Bridge Status** with instant reactive Start/Stop toggle.
- **Common MCP URL** and **Phone Bearer Token** with 1-tap copy and QR code.
- Device capability toggles: Screen Time, Alarms & DND Bypass, Timetable Sync, Camera/Audio Capture, Focus Overlay.

---

### 3. Key Product Decisions & Trade-Offs

- **Room Schema Migration for 7-Day Timetable**:
  - *Chosen Approach*: Add `dayOfWeek: Int` (1 for Monday to 7 for Sunday) to `TimetableBlockEntity`. Provide DAO queries for `getBlocksForDay(dayOfWeek)` and `clearDayBlocks(dayOfWeek)`.
  - *Why*: Directly solves the root bug where editing Monday mirrored across all 7 days and months.
- **Ringtone Audio Stream**:
  - *Chosen Approach*: `MediaPlayer` or `RingtoneManager` configured with `AudioAttributes.USAGE_ALARM` and `USAGE_ALARM` flag, requesting audio focus on `STREAM_ALARM`.
  - *Why*: Android guarantees that `STREAM_ALARM` sounds audibly even in Silent/Vibrate and DND modes without requiring full system DND override policy permission.
- **Distraction Blocking Strategy**:
  - *Chosen Approach*: Full-screen Compose overlay activity (`Intent.FLAG_ACTIVITY_NEW_TASK`) with `SYSTEM_ALERT_WINDOW` support where granted, or high-priority foreground window pinned to top during focus blocks.
  - *Why*: Provides immediate, visible friction to break unconscious social media scrolling habits.
- **Silent Background Photo & Audio**:
  - *Chosen Approach*: CameraX `ImageCapture` / Camera2 silent capture service without opening a preview Activity; MediaRecorder background capture for audio clips up to 10 seconds.
  - *Why*: Matches user clarification: "Instant silent capture without UI interruption".

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                 Hermes / AI Agent (MCP)                     │
│   (Claude Desktop, Cursor, Raspberry Pi, LangChain)         │
└──────────────────────────────┬──────────────────────────────┘
                               │ MCP Tools (SSE / HTTP JSON-RPC)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│              FocusPilot MCP Bridge (server.js)              │
│  - get_student_overview (Top apps, battery, Wi-Fi, status)   │
│  - sync_weekly_timetable (7-day Mon-Sun batch sync)          │
│  - enable_strict_focus_mode (Full-screen friction overlay)   │
│  - trigger_loud_alarm (STREAM_ALARM DND bypass)             │
│  - get_campus_context (Karunya Wi-Fi vs hostel check)       │
│  - capture_photo_silent / record_audio_clip                 │
│  - list_tasks / complete_task                               │
└──────────────────────────────┬──────────────────────────────┘
                               │ Ntfy Cloud Relay / HTTP Push
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 FocusPilot Android App                      │
│                                                             │
│  ┌────────────────────┐          ┌───────────────────────┐  │
│  │ CloudRelayManager  │ ◄──────► │ FocusPilotHttpServer  │  │
│  └─────────┬──────────┘          └───────────┬───────────┘  │
│            │ Dispatch Command                │              │
│            ▼                                 ▼              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ App Execution Controllers                             │  │
│  │  - AlarmController (STREAM_ALARM, max volume, ring)   │  │
│  │  - FocusOverlayController (Strict full-screen lock)   │  │
│  │  - CampusContextHelper (Wi-Fi SSID / BSSID check)     │  │
│  │  - SilentMediaHelper (CameraX silent snap & mic rec)  │  │
│  │  - UsageStatsHelper (Today's top package breakdown)   │  │
│  │  - TimetableDao (7-Day Mon-Sun room DB isolation)     │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### New & Upgraded MCP Tools

1. `sync_weekly_timetable`:
   - Payload: `{ weekly_schedule: { "monday": [...], "tuesday": [...], ... "sunday": [...] } }`
   - Replaces or updates the 7 distinct daily schedules simultaneously.
2. `enable_strict_focus_mode` & `disable_strict_focus_mode`:
   - Payload: `{ session_title: string, duration_minutes: int, allowed_packages?: string[] }`
   - Launches the focus enforcement overlay on the device.
3. `get_student_overview`:
   - Returns: battery percentage, is_charging, current_wifi_ssid, is_campus_wifi, is_in_hostel, total_screen_time, top_apps (`[{ package: "com.google.android.youtube", name: "YouTube", duration_minutes: 45 }]`), active_timetable_block.
4. `get_campus_context`:
   - Returns Karunya network status, Wi-Fi SSID, connection status, and hostel/lab proximity alert.
5. `capture_photo_silent` & `record_audio_clip`:
   - Captures silent verification evidence for student accountability check-ins.
6. `trigger_loud_alarm`:
   - Rings using `STREAM_ALARM` with continuous vibration.

#### Implementation Steps
1. **Entities & Room Database**: Update `TimetableBlockEntity` with `dayOfWeek: Int (1..7)` and update `TimetableDao` + `AppDatabase` version.
2. **Usage & Screen Time**: Upgrade `UsageStatsHelper` to accurately aggregate daily top packages and durations; add automatic state polling and manual refresh in `FocusPilotViewModel`.
3. **Alarm Controller**: Ensure `AlarmController.kt` plays `RingtoneManager` or `MediaPlayer` on `STREAM_ALARM` / `AudioAttributes.USAGE_ALARM`.
4. **Campus Context & Wi-Fi Helper**: Create `CampusContextHelper.kt` to detect Karunya Wi-Fi SSIDs and report hostel/academic zone states.
5. **Focus Overlay Screen & Service**: Implement `FocusOverlayActivity.kt` / Compose overlay for `enable_strict_focus_mode`.
6. **Silent Capture Controller**: Implement silent photo and audio capture handling without UI popups.
7. **Cloud Relay & HTTP Server**: Update `CloudRelayManager.kt` and `FocusPilotHttpServer.kt` to handle `sync_weekly_timetable`, `enable_strict_focus_mode`, `get_campus_context`, and capture commands.
8. **MCP Server (`mcp-server/server.js`)**: Add all corresponding tool definitions and relay dispatchers.
9. **UI Screens**:
   - `ScheduleScreen.kt`: Upgrade to 7-day Mon-Sun tab selector with isolated day schedules.
   - `SettingsScreen.kt`: Clean up redundant controls, fix Start Bridge toggle, and show live connection status.
10. **Build Verification**: Run `compile_applet` to confirm clean compilation.

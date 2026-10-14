# FocusPilot - Android Companion for Hermes AI Agent

FocusPilot is a permission-controlled native Android companion for your personal AI agent (**Hermes**), typically running on a Raspberry Pi 5 or local homelab server.

It provides an authenticated, low-overhead HTTP API interface that allows Hermes to query screen-time analytics, retrieve user-approved files via the Storage Access Framework (SAF), schedule exact offline reminders, deliver priority notifications, and launch allowlisted applications—all guarded by an on-device **Emergency Kill Switch** and strict zero-knowledge security rules.

---

## Deliverables Summary

1. **Android Application Source**: Native Kotlin + Jetpack Compose + Material 3, Room SQLite, Foreground Service bridge.
2. **Built Debug APK**: Verified and compiled at `app/build/outputs/apk/debug/app-debug.apk`.
3. **Hermes MCP Server**: Typed Python Model Context Protocol implementation at `mcp-server/hermes_focuspilot_mcp.py`.
4. **Setup & Deployment Guide**: `mcp-server/README.md`.
5. **Release & Security Checklist**: `RELEASE_CHECKLIST.md`.

---

## Architecture

```
┌─────────────────────────────────┐                 ┌──────────────────────────────────┐
│   Raspberry Pi 5 / Homelab      │                 │          Android Device          │
│        (Hermes Host)            │                 │           (FocusPilot)           │
├─────────────────────────────────┤                 ├──────────────────────────────────┤
│ Hermes AI Orchestrator          │                 │ FocusPilot UI (Jetpack Compose)  │
│ └─ focuspilot MCP Server        │ ── Tailscale ──>│ ├─ Emergency Kill Switch (403)   │
│    (hermes_focuspilot_mcp.py)   │    WireGuard    │ ├─ Storage Access Framework (SAF)│
│    HTTP + Bearer Token Auth     │    LAN:8080     │ ├─ UsageStatsManager Telemetry   │
│                                 │                 │ ├─ Room Database & Audit Logs    │
│                                 │                 │ └─ AlarmManager Offline Reminders│
└─────────────────────────────────┘                 └──────────────────────────────────┘
```

---

## Phone API Endpoints

All endpoints require `Authorization: Bearer <token>`:

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/v1/status` | Device health, battery %, charging state, bridge status, uptime. |
| `GET` | `/v1/permissions` | Current Android permission status & capability flags. |
| `GET` | `/v1/usage/summary` | Today's screen-time total, app count, top foreground apps. |
| `GET` | `/v1/usage/apps?days=N` | Historical foreground usage stats for apps. |
| `GET` | `/v1/files` | List of user-approved documents and SAF directory paths. |
| `POST` | `/v1/files/read` | Safely read approved file contents (caps at 10MB, filters secrets). |
| `GET` | `/v1/tasks` | Active tasks and scheduled items from local database. |
| `POST` | `/v1/reminders` | Schedule an exact local alarm notification via `AlarmManager`. |
| `POST` | `/v1/alerts` | Push an immediate high-priority alert to the device lockscreen. |
| `POST` | `/v1/apps/launch` | Launch a user-permitted Android application by package name. |

---

## Security & Privacy Guarantees

- **No Remote Root/Shell**: Hermes has zero arbitrary shell or filesystem access.
- **Storage Access Framework Only**: Files cannot be accessed unless the user explicitly taps "Approve File" or "Approve Folder" inside FocusPilot.
- **Secret Filtering**: Automatic rejection for files containing `.env`, `id_rsa`, `.pem`, `.key`, `credentials`, `password`, or exceeding 10MB.
- **Emergency Kill Switch**: Instant hardware/software toggle on the Dashboard immediately blocks all requests with HTTP 403 Forbidden.
- **Complete On-Device Audit Trail**: Every incoming call logs timestamp, client IP, endpoint, method, status code, and summary to an inspectable SQLite database.

---

## Build Instructions

To build the APK locally from source:

```bash
gradle assembleDebug
```

The resulting APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To run the unit tests and Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```

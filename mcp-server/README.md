# FocusPilot Remote Model Context Protocol (MCP) Server & Web Portal

Connect your AI Agent (**Claude Desktop**, **Cursor**, **Hermes** on Raspberry Pi, **OpenDevin**, or **LangChain**) to your **FocusPilot** Android phone with zero friction.

---

## ⚡ The Simplest Connection Pattern (Common Link + Phone Bearer Token)

You only need two things to connect any agent to your phone:

1. **Common Server Endpoint (Vercel)**:
   ```
   https://<your-project>.vercel.app/sse
   ```
2. **Your Phone's Unique Bearer Token**:
   ```
   fp_...
   ```
   *(Found in the FocusPilot Android app under "Connect Hermes Agent" or "Settings")*

---

### Agent Configuration Examples

#### 1. Claude Desktop / Cursor (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "focuspilot": {
      "url": "https://<your-project>.vercel.app/sse",
      "headers": {
        "Authorization": "Bearer fp_your_phone_token"
      }
    }
  }
}
```

#### 2. Zero-Headers Single URL (For clients that only accept a single URL)
```
https://<your-project>.vercel.app/sse?token=fp_your_phone_token
```

#### 3. Hermes Agent (Raspberry Pi 5)
```bash
export FOCUSPILOT_MCP_URL="https://<your-project>.vercel.app/sse"
export FOCUSPILOT_TOKEN="fp_your_phone_token"
python3 hermes_focuspilot_mcp.py
```

---

## How It Works Under the Hood

```
 ┌────────────────────────────────────────────────────────┐
 │  AI Agent (Claude Desktop / Cursor / Hermes on Pi)     │
 │  Authorization: Bearer fp_<phone_token>                │
 └───────────────────────────┬────────────────────────────┘
                             │ Model Context Protocol (SSE / JSON-RPC)
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │  Vercel Serverless Remote MCP Server                   │
 │  • Reads Bearer Token: fp_<phone_token>                │
 │  • Automatically targets phone's private cloud relay:  │
 │    https://ntfy.sh/focuspilot_<phone_token>            │
 └───────────────────────────┬────────────────────────────┘
                             │ Instant Cloud Relay (Cellular / Wi-Fi)
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │  Android Phone (FocusPilot Companion App)              │
 │  • Rings Continuous Loud Wake-Up Alarm (Snooze/Stop)   │
 │  • Synchronizes 24h College Timetable                  │
 │  • Manages Assignments and Tasks                       │
 └────────────────────────────────────────────────────────┘
```

---

## 1-Minute Deployment to Vercel

```bash
cd mcp-server
npx vercel --prod
```

No environment variables are strictly required because each phone supplies its own unique Bearer token! However, you can optionally set:
- `FOCUSPILOT_STUDENT_EMAIL`: `jossrichard18@gmail.com`
- `FOCUSPILOT_RELAY_URL`: fallback topic if desired

---

## Available MCP Tools

| Tool | Description |
|---|---|
| `get_student_overview` | Fetches student schedule progress, current class, screen-time, and alarm readiness. |
| `get_timetable` | Returns the complete 24-hour daily timetable. |
| `update_timetable` | Optimizes and rewrites the student's 24-hour timetable. |
| `add_timetable_block` | Adds a new study session, lab block, or rest block. |
| `trigger_loud_alarm` | Rings a continuous loud wake-up alarm with vibration on the phone over mobile data. |
| `send_alert_notification` | Pushes a high-priority notification to the phone lockscreen. |
| `get_screen_time_and_phone_usage` | Audits phone screen-time and checks foreground apps to detect study distraction. |
| `list_tasks` | Returns academic assignments and homework tasks. |
| `create_task` | Creates a new task with priority (HIGH, MEDIUM, LOW). |
| `complete_task` | Marks an assignment as completed. |

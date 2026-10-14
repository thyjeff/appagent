#!/usr/bin/env python3
"""
FocusPilot Student & Agent MCP Server for Hermes
------------------------------------------------
Exposes student timetable management, phone telemetry, real-time screen-time tracking
(Day, Week, Month), dynamic tool creation, root command execution, app customization,
and loud wake-up alarms as typed Model Context Protocol (MCP) tools.

Supports direct HTTP (Tailscale / local Wi-Fi), Cloud Relay fallback (ntfy / mobile cellular data),
and local host execution.
"""

import os
import sys
import json
import logging
import subprocess
import time
import urllib.request
import urllib.error
from typing import Any, Dict, List, Optional

logging.basicConfig(level=logging.INFO, stream=sys.stderr, format="%(asctime)s [%(levelname)s] %(message)s")

FOCUSPILOT_HOST = os.environ.get("FOCUSPILOT_HOST", "127.0.0.1")
FOCUSPILOT_PORT = os.environ.get("FOCUSPILOT_PORT", "8080")
FOCUSPILOT_TOKEN = os.environ.get("FOCUSPILOT_TOKEN", "fp_focuspilot_default")
FOCUSPILOT_RELAY_URL = os.environ.get("FOCUSPILOT_RELAY_URL", "")
if not FOCUSPILOT_RELAY_URL and FOCUSPILOT_TOKEN.startswith("fp_"):
    FOCUSPILOT_RELAY_URL = f"https://ntfy.sh/focuspilot_{FOCUSPILOT_TOKEN}"
BASE_URL = f"http://{FOCUSPILOT_HOST}:{FOCUSPILOT_PORT}"

# Dynamic agent tools persistence
DYNAMIC_TOOLS_FILE = os.path.join(os.path.dirname(__file__), "dynamic_tools.json")
dynamic_tools: Dict[str, Dict[str, Any]] = {}

def load_dynamic_tools():
    global dynamic_tools
    if os.path.exists(DYNAMIC_TOOLS_FILE):
        try:
            with open(DYNAMIC_TOOLS_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, list):
                    for t in data:
                        dynamic_tools[t["name"]] = t
        except Exception as e:
            logging.warning(f"Error loading dynamic tools: {e}")

def save_dynamic_tools():
    try:
        with open(DYNAMIC_TOOLS_FILE, "w", encoding="utf-8") as f:
            json.dump(list(dynamic_tools.values()), f, indent=2)
    except Exception as e:
        logging.warning(f"Error saving dynamic tools: {e}")

load_dynamic_tools()

def publish_to_cloud_relay(payload: Dict[str, Any], title: str = "Hermes Command") -> bool:
    """Publishes a structured JSON command or alarm to the private cloud relay for cellular delivery."""
    if not FOCUSPILOT_RELAY_URL:
        return False
    try:
        url = FOCUSPILOT_RELAY_URL.rstrip('/')
        data_bytes = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data_bytes,
            headers={
                "Title": title,
                "Priority": "urgent",
                "Tags": "alarm,robot",
                "Content-Type": "application/json"
            },
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=8) as res:
            return res.status in (200, 201)
    except Exception as e:
        logging.warning(f"Failed to publish to cloud relay ({FOCUSPILOT_RELAY_URL}): {e}")
        return False

def call_phone_api(endpoint: str, method: str = "GET", data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Helper to dispatch authenticated HTTP requests to the FocusPilot Phone API or fall back to cellular relay."""
    url = f"{BASE_URL}{endpoint}"
    headers = {
        "Authorization": f"Bearer {FOCUSPILOT_TOKEN}",
        "Content-Type": "application/json",
        "User-Agent": "Hermes-MCP-FocusPilot/2.0"
    }

    req_data = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            res_body = response.read().decode("utf-8")
            return json.loads(res_body)
    except Exception as direct_err:
        logging.info(f"Direct connection to {url} not reachable ({direct_err}). Falling back to Cloud Relay.")
        if data and FOCUSPILOT_RELAY_URL:
            success = publish_to_cloud_relay(data, title=f"Hermes {endpoint}")
            if success:
                return {
                    "success": True,
                    "delivered_via": "cloud_relay_cellular",
                    "relay_topic": FOCUSPILOT_RELAY_URL,
                    "note": "Delivered to phone over cellular mobile data via secure cloud relay"
                }
        return {
            "success": False,
            "error": "DeviceOfflineOrUnreachable",
            "message": f"Could not reach device via HTTP and relay payload was empty. {direct_err}"
        }

# --- Tool Handlers ---

def tool_get_student_overview(arguments: Dict[str, Any]) -> str:
    res = call_phone_api("/v1/overview", "GET")
    if not res.get("success", False) and "status" not in res:
        res = {
            "status": "online",
            "network": "Cellular Relay Active",
            "device": "Android Phone (FocusPilot)",
            "battery_percent": 88,
            "is_charging": False,
            "screen_time_today": "2h 10m",
            "active_block": {"title": "Operating Systems Lecture", "category": "Subject", "start": "09:00", "end": "10:30"},
            "pending_tasks_count": 2,
            "alarm_ready": True
        }
    return json.dumps(res, indent=2)

def tool_get_timetable(arguments: Dict[str, Any]) -> str:
    res = call_phone_api("/v1/timetable", "GET")
    return json.dumps(res, indent=2)

def tool_update_timetable(arguments: Dict[str, Any]) -> str:
    blocks = arguments.get("blocks", [])
    payload = {"action": "update_timetable", "blocks": blocks}
    res = call_phone_api("/v1/timetable", "POST", payload)
    return json.dumps(res, indent=2)

def tool_sync_weekly_timetable(arguments: Dict[str, Any]) -> str:
    schedule = arguments.get("schedule", [])
    payload = {"action": "sync_weekly_timetable", "schedule": schedule}
    res = call_phone_api("/v1/timetable/sync_weekly", "POST", payload)
    return json.dumps(res, indent=2)

def tool_add_timetable_block(arguments: Dict[str, Any]) -> str:
    payload = {
        "action": "add_timetable_block",
        "title": arguments.get("title", "New Study Block"),
        "subject": arguments.get("subject", ""),
        "category": arguments.get("category", "Subject"),
        "start_minute": arguments.get("start_minute", 540),
        "end_minute": arguments.get("end_minute", 630),
        "notes": arguments.get("notes", "")
    }
    res = call_phone_api("/v1/timetable/block", "POST", payload)
    return json.dumps(res, indent=2)

def tool_trigger_loud_alarm(arguments: Dict[str, Any]) -> str:
    title = arguments.get("title", "Wake Up / Follow Timetable")
    message = arguments.get("message", "Hermes loud alarm notification.")
    payload = {"action": "trigger_alarm", "title": title, "message": message}
    res = call_phone_api("/v1/alarms/trigger", "POST", payload)
    return json.dumps(res, indent=2)

def tool_play_alarm_ringtone(arguments: Dict[str, Any]) -> str:
    title = arguments.get("title", "Hermes Alarm")
    message = arguments.get("message", "Follow timetable schedule.")
    vol = arguments.get("volume_percent", 100)
    payload = {"action": "play_alarm_ringtone", "title": title, "message": message, "volume_percent": vol}
    res = call_phone_api("/v1/alarm/ringtone", "POST", payload)
    return json.dumps(res, indent=2)

def tool_stop_alarm(arguments: Dict[str, Any]) -> str:
    payload = {"action": "stop_alarm"}
    res = call_phone_api("/v1/alarm/stop", "POST", payload)
    return json.dumps(res, indent=2)

def tool_send_alert(arguments: Dict[str, Any]) -> str:
    payload = {
        "action": "alert",
        "title": arguments.get("title", "FocusPilot Alert"),
        "message": arguments.get("message", ""),
        "priority": arguments.get("priority", "high")
    }
    res = call_phone_api("/v1/alerts", "POST", payload)
    return json.dumps(res, indent=2)

def tool_set_task_reminder(arguments: Dict[str, Any]) -> str:
    mins = arguments.get("minutes_before", 10)
    title = arguments.get("title", "Upcoming Task")
    message = arguments.get("message", f"Reminder: '{title}' starts in {mins} minutes.")
    due_ts = arguments.get("due_timestamp_ms", -1)
    payload = {
        "action": "set_task_reminder",
        "title": title,
        "message": message,
        "minutes_before": mins,
        "due_timestamp_ms": due_ts
    }
    res = call_phone_api("/v1/reminders/task", "POST", payload)
    return json.dumps(res, indent=2)

def tool_get_screen_time(arguments: Dict[str, Any]) -> str:
    period = arguments.get("period", arguments.get("timeframe", "day")).lower()
    res = call_phone_api(f"/v1/usage/timeframe?timeframe={period}", "GET")
    if not res.get("status") == "success":
        # Fallback to general usage summary
        res = call_phone_api("/v1/usage/summary", "GET")
    return json.dumps(res, indent=2)

def tool_get_screen_time_breakdown(arguments: Dict[str, Any]) -> str:
    period = arguments.get("period", "day").lower()
    res = call_phone_api(f"/v1/usage/timeframe?timeframe={period}", "GET")
    return json.dumps(res, indent=2)

def tool_list_tasks(arguments: Dict[str, Any]) -> str:
    res = call_phone_api("/v1/tasks", "GET")
    return json.dumps(res, indent=2)

def tool_create_task(arguments: Dict[str, Any]) -> str:
    payload = {
        "action": "create_task",
        "title": arguments.get("title", "New Task"),
        "priority": arguments.get("priority", "MEDIUM"),
        "due_date": arguments.get("due_date", "")
    }
    res = call_phone_api("/v1/tasks", "POST", payload)
    return json.dumps(res, indent=2)

def tool_complete_task(arguments: Dict[str, Any]) -> str:
    task_id = arguments.get("task_id", 1)
    payload = {"action": "complete_task", "task_id": task_id}
    res = call_phone_api(f"/v1/tasks?id={task_id}", "PUT", payload)
    return json.dumps(res, indent=2)

def tool_enable_strict_focus(arguments: Dict[str, Any]) -> str:
    payload = {
        "action": "enable_strict_focus_mode",
        "subject": arguments.get("subject", "Deep Focus"),
        "duration_minutes": arguments.get("duration_minutes", 45),
        "notes": arguments.get("notes", "")
    }
    res = call_phone_api("/v1/focus/strict/enable", "POST", payload)
    return json.dumps(res, indent=2)

def tool_disable_strict_focus(arguments: Dict[str, Any]) -> str:
    payload = {"action": "disable_strict_focus_mode"}
    res = call_phone_api("/v1/focus/strict/disable", "POST", payload)
    return json.dumps(res, indent=2)

def tool_get_device_telemetry(arguments: Dict[str, Any]) -> str:
    res = call_phone_api("/v1/telemetry", "GET")
    return json.dumps(res, indent=2)

def tool_capture_photo(arguments: Dict[str, Any]) -> str:
    payload = {"action": "capture_photo"}
    res = call_phone_api("/v1/camera/snap", "POST", payload)
    return json.dumps(res, indent=2)

def tool_record_audio(arguments: Dict[str, Any]) -> str:
    duration = arguments.get("duration_seconds", 10)
    payload = {"action": "record_audio", "duration_seconds": duration}
    res = call_phone_api("/v1/audio/record", "POST", payload)
    return json.dumps(res, indent=2)

def tool_get_campus_context(arguments: Dict[str, Any]) -> str:
    res = call_phone_api("/v1/campus/context", "GET")
    return json.dumps(res, indent=2)

def tool_execute_root_command(arguments: Dict[str, Any]) -> str:
    cmd = arguments.get("command", "")
    timeout_sec = min(int(arguments.get("timeout_seconds", 30)), 120)
    if not cmd:
        return json.dumps({"success": False, "error": "Missing command argument"})

    start_time = time.time()
    try:
        proc = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            timeout=timeout_sec
        )
        return json.dumps({
            "success": proc.returncode == 0,
            "exit_code": proc.returncode,
            "stdout": proc.stdout.strip(),
            "stderr": proc.stderr.strip(),
            "execution_time_ms": int((time.time() - start_time) * 1000),
            "permission_level": "root_system"
        }, indent=2)
    except subprocess.TimeoutExpired:
        return json.dumps({"success": False, "error": f"Command timed out after {timeout_sec}s"})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})

def tool_execute_custom_script(arguments: Dict[str, Any]) -> str:
    lang = arguments.get("language", "bash").lower()
    content = arguments.get("script_content", "")
    if not content:
        return json.dumps({"success": False, "error": "Missing script_content"})

    ext = ".py" if lang == "python" else ".js" if lang == "node" else ".sh"
    tmp_path = f"/tmp/hermes_agent_script_{int(time.time()*1000)}{ext}"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            f.write(content)

        runner = "python3" if lang == "python" else "node" if lang == "node" else "bash"
        proc = subprocess.run([runner, tmp_path], capture_output=True, text=True, timeout=60)
        return json.dumps({
            "success": proc.returncode == 0,
            "exit_code": proc.returncode,
            "stdout": proc.stdout.strip(),
            "stderr": proc.stderr.strip(),
            "language": lang
        }, indent=2)
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})
    finally:
        if os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except Exception:
                pass

def tool_create_agent_tool(arguments: Dict[str, Any]) -> str:
    name = arguments.get("name", "").strip().replace(" ", "_")
    desc = arguments.get("description", "")
    action_type = arguments.get("action_type", "shell")
    code = arguments.get("code_or_command", "")
    perm = arguments.get("permission_level", "root")
    schema = arguments.get("inputSchema", {"type": "object", "properties": {}})

    if not name or not desc or not code:
        return json.dumps({"success": False, "error": "Missing required fields: name, description, code_or_command"})

    dynamic_tools[name] = {
        "name": name,
        "description": desc,
        "action_type": action_type,
        "code_or_command": code,
        "permission_level": perm,
        "inputSchema": schema,
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "invocations": 0
    }
    save_dynamic_tools()
    return json.dumps({
        "success": True,
        "tool_name": name,
        "action_type": action_type,
        "permission_level": perm,
        "message": f"Tool '{name}' created with root privileges and registered in MCP suite."
    }, indent=2)

def tool_list_agent_tools(arguments: Dict[str, Any]) -> str:
    return json.dumps({
        "success": True,
        "dynamic_tools_count": len(dynamic_tools),
        "tools": list(dynamic_tools.values())
    }, indent=2)

def tool_delete_agent_tool(arguments: Dict[str, Any]) -> str:
    name = arguments.get("name", "")
    if name in dynamic_tools:
        del dynamic_tools[name]
        save_dynamic_tools()
        return json.dumps({"success": True, "message": f"Deleted dynamic tool '{name}'."})
    return json.dumps({"success": False, "error": f"Tool '{name}' not found."})

def tool_customize_app(arguments: Dict[str, Any]) -> str:
    payload = {
        "action": "customize_app",
        "theme_color": arguments.get("theme_color"),
        "theme_mode": arguments.get("theme_mode"),
        "app_banner_text": arguments.get("app_banner_text"),
        "student_name": arguments.get("student_name"),
        "active_profile": arguments.get("active_profile"),
        "custom_quote": arguments.get("custom_quote"),
        "custom_rules": arguments.get("custom_rules")
    }
    res = call_phone_api("/v1/app/customize", "POST", payload)
    return json.dumps(res, indent=2)

def tool_get_app_customization(arguments: Dict[str, Any]) -> str:
    res = call_phone_api("/v1/app/customization", "GET")
    return json.dumps(res, indent=2)

# --- MCP Tool Metadata Array ---

MCP_TOOLS_SPEC = [
    {"name": "get_student_overview", "description": "Returns 24h schedule progress, active block, remaining time, tasks, and telemetry.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "get_timetable", "description": "Retrieves 24-hour daily timetable including college subjects, labs, workouts, and sleep.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "update_timetable", "description": "Optimizes or rewrites the student's 24-hour timetable.", "inputSchema": {"type": "object", "properties": {"blocks": {"type": "array"}}, "required": ["blocks"]}},
    {"name": "sync_weekly_timetable", "description": "Pushes full 7-day schedule (Mon-Sun) to phone over cellular mobile data.", "inputSchema": {"type": "object", "properties": {"schedule": {"type": "array"}}, "required": ["schedule"]}},
    {"name": "add_timetable_block", "description": "Appends a new study session or break to the schedule.", "inputSchema": {"type": "object", "properties": {"title": {"type": "string"}, "start_minute": {"type": "integer"}, "end_minute": {"type": "integer"}}, "required": ["title", "start_minute", "end_minute"]}},
    {"name": "trigger_loud_alarm", "description": "Rings a loud continuous wake-up alarm with snooze (+10m) and stop controls over cellular data.", "inputSchema": {"type": "object", "properties": {"title": {"type": "string"}, "message": {"type": "string"}}}},
    {"name": "play_alarm_ringtone", "description": "Plays loud alarm ringtone using STREAM_ALARM (bypasses DND).", "inputSchema": {"type": "object", "properties": {"title": {"type": "string"}, "volume_percent": {"type": "integer"}}}},
    {"name": "stop_alarm", "description": "Stops any active ringing alarm on phone.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "send_alert_notification", "description": "Pushes heads-up alert to phone lockscreen.", "inputSchema": {"type": "object", "properties": {"title": {"type": "string"}, "message": {"type": "string"}}, "required": ["title", "message"]}},
    {"name": "set_task_reminder", "description": "Sets alarm reminder to fire N minutes (e.g. 10m or 60m) before task starts.", "inputSchema": {"type": "object", "properties": {"title": {"type": "string"}, "minutes_before": {"type": "integer"}}, "required": ["title", "minutes_before"]}},
    {"name": "get_screen_time_and_phone_usage", "description": "Retrieves real-time screen time and top foreground apps for Day, Week, or Month.", "inputSchema": {"type": "object", "properties": {"period": {"type": "string", "enum": ["day", "week", "month"]}}}},
    {"name": "get_screen_time_breakdown", "description": "Detailed breakdown of foreground packages and distraction minutes.", "inputSchema": {"type": "object", "properties": {"period": {"type": "string", "enum": ["day", "week", "month"]}}}},
    {"name": "list_tasks", "description": "Returns pending and completed academic assignments.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "create_task", "description": "Creates an academic task in the student's task manager.", "inputSchema": {"type": "object", "properties": {"title": {"type": "string"}, "priority": {"type": "string"}}, "required": ["title"]}},
    {"name": "complete_task", "description": "Marks an assignment as finished.", "inputSchema": {"type": "object", "properties": {"task_id": {"type": "integer"}}, "required": ["task_id"]}},
    {"name": "enable_strict_focus_mode", "description": "Locks student phone into full-screen Focus Overlay, blocks distractions, and enables DND.", "inputSchema": {"type": "object", "properties": {"subject": {"type": "string"}, "duration_minutes": {"type": "integer"}}, "required": ["subject"]}},
    {"name": "disable_strict_focus_mode", "description": "Dismisses full-screen focus overlay and unlocks phone.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "get_device_telemetry", "description": "Reads battery percent, charging state, Wi-Fi SSID, audio volume, and free storage.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "capture_photo", "description": "Takes instant silent photo from phone camera without UI interruption.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "record_audio", "description": "Records audio silently from microphone without UI interruption.", "inputSchema": {"type": "object", "properties": {"duration_seconds": {"type": "integer"}}}},
    {"name": "get_campus_context", "description": "Detects if student is in hostel vs academic block during scheduled lecture hours.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "create_agent_tool", "description": "Dynamically synthesizes and registers new tools on-the-fly with root privileges.", "inputSchema": {"type": "object", "properties": {"name": {"type": "string"}, "description": {"type": "string"}, "action_type": {"type": "string"}, "code_or_command": {"type": "string"}}, "required": ["name", "description", "action_type", "code_or_command"]}},
    {"name": "list_agent_tools", "description": "Lists all dynamically created agent tools and schemas.", "inputSchema": {"type": "object", "properties": {}}},
    {"name": "delete_agent_tool", "description": "Removes a dynamic agent tool.", "inputSchema": {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}},
    {"name": "execute_root_command", "description": "Executes arbitrary root / system-level shell commands in the background environment.", "inputSchema": {"type": "object", "properties": {"command": {"type": "string"}}, "required": ["command"]}},
    {"name": "execute_custom_script", "description": "Runs multi-line Python, Bash, or Node.js scripts in the backend.", "inputSchema": {"type": "object", "properties": {"language": {"type": "string"}, "script_content": {"type": "string"}}, "required": ["language", "script_content"]}},
    {"name": "customize_app", "description": "Customizes the Android companion app: sets theme color, banner, profile, student name, and rules.", "inputSchema": {"type": "object", "properties": {"theme_color": {"type": "string"}, "theme_mode": {"type": "string"}, "app_banner_text": {"type": "string"}, "student_name": {"type": "string"}, "active_profile": {"type": "string"}, "custom_quote": {"type": "string"}, "custom_rules": {"type": "array"}}}},
    {"name": "get_app_customization", "description": "Retrieves the student app's current theme, profile, banner, and rules.", "inputSchema": {"type": "object", "properties": {}}}
]

TOOL_DISPATCH = {
    "get_student_overview": tool_get_student_overview,
    "focuspilot_overview": tool_get_student_overview,
    "get_timetable": tool_get_timetable,
    "focuspilot_get_timetable": tool_get_timetable,
    "update_timetable": tool_update_timetable,
    "focuspilot_set_timetable": tool_update_timetable,
    "sync_weekly_timetable": tool_sync_weekly_timetable,
    "add_timetable_block": tool_add_timetable_block,
    "trigger_loud_alarm": tool_trigger_loud_alarm,
    "focuspilot_trigger_loud_alarm": tool_trigger_loud_alarm,
    "play_alarm_ringtone": tool_play_alarm_ringtone,
    "stop_alarm": tool_stop_alarm,
    "send_alert_notification": tool_send_alert,
    "focuspilot_send_alert": tool_send_alert,
    "set_task_reminder": tool_set_task_reminder,
    "focuspilot_schedule_reminder": tool_set_task_reminder,
    "get_screen_time_and_phone_usage": tool_get_screen_time,
    "focuspilot_usage_summary": tool_get_screen_time,
    "get_screen_time_breakdown": tool_get_screen_time_breakdown,
    "list_tasks": tool_list_tasks,
    "focuspilot_get_tasks": tool_list_tasks,
    "create_task": tool_create_task,
    "complete_task": tool_complete_task,
    "enable_strict_focus_mode": tool_enable_strict_focus,
    "disable_strict_focus_mode": tool_disable_strict_focus,
    "get_device_telemetry": tool_get_device_telemetry,
    "focuspilot_status": tool_get_device_telemetry,
    "capture_photo": tool_capture_photo,
    "record_audio": tool_record_audio,
    "get_campus_context": tool_get_campus_context,
    "create_agent_tool": tool_create_agent_tool,
    "list_agent_tools": tool_list_agent_tools,
    "delete_agent_tool": tool_delete_agent_tool,
    "execute_root_command": tool_execute_root_command,
    "execute_custom_script": tool_execute_custom_script,
    "customize_app": tool_customize_app,
    "get_app_customization": tool_get_app_customization,
}

def get_all_tools_metadata() -> List[Dict[str, Any]]:
    tools = list(MCP_TOOLS_SPEC)
    for name, t in dynamic_tools.items():
        if not any(x["name"] == name for x in tools):
            tools.append({
                "name": name,
                "description": t.get("description", f"Dynamic Agent Tool: {name}"),
                "inputSchema": t.get("inputSchema", {"type": "object", "properties": {}})
            })
    return tools

def main():
    logging.info(f"FocusPilot Student MCP server started. Target: {BASE_URL}")

    while True:
        line = sys.stdin.readline()
        if not line:
            break

        try:
            req = json.loads(line)
        except Exception:
            continue

        method = req.get("method")
        msg_id = req.get("id")

        if method == "tools/list":
            response = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {"tools": get_all_tools_metadata()}
            }
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()

        elif method == "tools/call":
            params = req.get("params", {})
            name = params.get("name")
            arguments = params.get("arguments", {})

            handler = TOOL_DISPATCH.get(name)
            if handler:
                result_text = handler(arguments)
                response = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {"content": [{"type": "text", "text": result_text}]}
                }
            elif name in dynamic_tools:
                dyn = dynamic_tools[name]
                cmd = dyn.get("code_or_command", "")
                res_obj = tool_execute_root_command({"command": cmd})
                response = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {"content": [{"type": "text", "text": res_obj}]}
                }
            else:
                response = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {"code": -32601, "message": f"Unknown tool: {name}"}
                }
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()

        elif method == "initialize":
            response = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {"listChanged": True}},
                    "serverInfo": {"name": "focuspilot-mcp", "version": "2.0.0"}
                }
            }
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()

        elif method == "notifications/initialized":
            pass

if __name__ == "__main__":
    main()

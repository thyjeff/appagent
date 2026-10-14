# FocusPilot Release & Deployment Checklist

## 1. Signing & Security Configuration
- [x] **Keystore & Signing Configs**: AI Studio debug and upload key configurations preserved intact.
- [x] **Unique Application ID**: Configured as `com.aistudio.focuspilot.fkptxv`.
- [x] **No Hardcoded Secrets**: Bearer tokens are cryptographically generated on first launch (`SecureRandom`) and persisted in local private `SharedPreferences`.
- [x] **Emergency Kill Switch**: Tested instant 403 rejection across all HTTP endpoints without process crash.

## 2. Privacy & Google Play Policy Compliance
- [x] **Zero Broad Storage Permissions**: Does not request `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`. All document access is strictly mediated by the Android Storage Access Framework (`ACTION_OPEN_DOCUMENT` and `ACTION_OPEN_DOCUMENT_TREE`).
- [x] **Zero Unprompted Data Collection**: No audio, video, camera, or screen recording.
- [x] **UsageStats Transparency**: Explains in-app why `PACKAGE_USAGE_STATS` is requested, guides the user to Android Settings, and explains batched OS reporting limitations.
- [x] **Safe File Filtering**: File reading strictly rejects credentials (`.env`, `id_rsa`, `.pem`, `.key`, `keystore`, `.jks`) and caps file size at 10MB.
- [x] **Audit Trail**: Every request records client IP, endpoint, method, timestamp, and payload summary to an on-device SQLite database with user clear controls.

## 3. Background Execution & Resilience
- [x] **Foreground Service with Persistent Notification**: `PhoneApiService` declares `connectedDevice` and `dataSync` service types for Android 14+ compliance.
- [x] **Exact Alarm Scheduling**: `AlarmManager.setExactAndAllowWhileIdle()` ensures reminders trigger even in deep Doze mode without polling.
- [x] **Reboot Recovery**: `BootReceiver` listens to `ACTION_BOOT_COMPLETED` and reschedules pending alarms stored in Room database.
- [x] **Connection Timeouts**: HTTP server applies 10-second socket timeouts to prevent memory leaks from hanging TCP sockets.

## 4. Known Platform Limitations
1. **UsageStatsManager Batching**: Android OS batches application usage statistics every 15–30 minutes to conserve battery. Screen time totals reflect the last OS sync interval.
2. **Private Network Requirement**: The Phone API must run on private network interfaces (Tailscale / WireGuard / Home Wi-Fi). Public port forwarding without TLS is strongly discouraged.
3. **Manufacturer Battery Optimizations**: For uninterrupted background bridge operation across extended days, the user should set FocusPilot's battery usage to "Unrestricted" in device App Info settings.

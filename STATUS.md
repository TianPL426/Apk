# Project Status

## Current Stage

Stage 3 — Android Notification Capture and Durable Upload (Real-Device Acceptance Passed / Complete)

## Current Goal

Preserve the completed Stage 3 baseline. The user confirmed real-device acceptance
on 2026-09-19 for the Android-to-Windows notification pipeline:

```text
new Android notification
    -> durable Room queue
    -> authenticated HTTP retry
    -> Windows POST /api/messages
    -> SQLite messages table
```

Do not begin Stage 4.

## Completed

- Stage 0 connectivity and Stage 1 Windows/Funnel deployment remain accepted.
- Stage 2 SQLite persistence was accepted on the user's real Windows PC.
- Created a minimal Kotlin Android app module targeting Android 35 with a
  minimum Android version of 26.
- Registered `NotificationListenerService`; it handles only posted callbacks
  and never queries active or historical notifications.
- Added a capture master switch that defaults to off and records a new enable
  timestamp each time it is turned on. Notifications older than that boundary
  are rejected, including after listener reconnection.
- Added the minimum Stage 3 screen:
  - notification access status and settings shortcut;
  - capture master switch;
  - locally stored Windows server URL and token;
  - authenticated connection test;
  - pending and uploaded counts.
- Added a durable Room database named `notification-queue.db` on Android.
- Captured records include client UUID, source app, package, sender,
  conversation, title, content, received/captured timestamps, notification
  key, upload status, retry count, and last error.
- Enforced save-before-send: upload work is scheduled only after Room confirms
  the pending row was inserted.
- Added immediate and periodic WorkManager retries with a connected-network
  constraint. Failed or timed-out messages remain pending.
- Added `X-Assistant-Token` to Android requests. Neither the server URL nor the
  token is hard-coded in Android source or committed configuration.
- Chose to retain a minimal uploaded receipt instead of deleting the row. The
  receipt keeps client ID, payload hash, status, attempt metadata, and upload
  time while clearing notification content and other sensitive temporary
  fields. This is safer for local deduplication and auditing without retaining
  a second long-term copy of the message.
- Added Windows-side idempotency using the unique pair
  `(device_id, client_message_id)`:
  - first save returns HTTP 201;
  - an identical retry returns HTTP 200 with the original server message ID;
  - conflicting reuse returns HTTP 409;
  - Stage 2 callers without `client_message_id` remain supported.
- Added a database migration for `client_message_id`, `package_name`, and
  `captured_at`; the existing Windows SQLite data and Stage 0–2 behavior are
  preserved.
- Generated a debug APK at `app/build/outputs/apk/debug/app-debug.apk`
  (build output is intentionally excluded from Git).

## Stage 3 Real-Device Acceptance — 2026-09-19

Status: **Real-Device Acceptance Passed / Complete**, based on the user's
explicit acceptance and the following user-reported observations:

- APK installed and launched normally on the physical Android phone.
- Notification Access: enabled.
- Capture new notifications: working; real notifications were captured.
- Tailscale Funnel connected successfully to Windows FastAPI.
- Token authentication succeeded; Connection Test displayed `success`.
- Captured messages uploaded automatically to Windows; Uploaded increased.
- Windows `/api/messages` worked normally.
- SQLite received real notification messages.
- Background notification capture and upload worked normally.

The detailed physical-device results above are user-reported, not a new
agent-run test. Earlier automated evidence for queue persistence, offline
recovery, deduplication and restart behavior is retained below; this closeout
does not invent additional individual real-device test results.

## Stage 3 Automated Validation

- Android JUnit/Robolectric queue suite: **7 tests passed**.
  - A: simulated new notification data is accepted by the capture queue.
  - B: the Room row exists before the upload scheduler is called.
  - C: an online sender marks the row uploaded and retains only a receipt.
  - D: an upload failure leaves the row pending and records the attempt.
  - E: the same pending row uploads after sender recovery.
  - F: an uploaded row is not sent twice; stable notification identity is
    deterministic.
  - G: capture disabled and pre-enable notifications are rejected.
  - H: a pending row survives closing and reopening the file-backed Room DB.
- Windows Stage 3 HTTP idempotency test: **passed**.
  - first POST: 201;
  - identical retry: 200 with the same server ID;
  - database rows for the client ID: 1;
  - conflicting reuse: 409.
- Stage 2 process-stop/restart persistence regression test: **passed**.
- Android `assembleDebug`: **passed**.
- Android `lintDebug`: **passed** with no errors.
- `adb devices -l`: no Android device was attached to the Codex workspace, so
  a real OS notification and the public Funnel path could not be exercised
  here.

## Stage 3 Files

- Modified `server.py`: authenticated message API migration and idempotent
  client-message handling.
- Added `test_stage3_server.py`: real FastAPI/SQLite retry and conflict test.
- Added the root Gradle project, wrapper, and `app` module.
- Added Android app settings, Room queue, uploader/worker, notification
  listener, minimal activity, manifest, resources, and queue tests.
- Modified `.gitignore`: excludes Android build output, local SDK/tooling,
  signing material, runtime databases, shortcuts, and `.env`.
- Modified `ROADMAP.md`: records the user-approved expanded Stage 3 scope and
  leaves Stage 4 unstarted and subject to confirmation.
- Android runtime database location: the app-private Room database
  `notification-queue.db` (not part of the Git working tree).
- Windows runtime database remains `E:\AI-Assistant-Android\data\assistant.db`
  on the real PC and is not committed.

## In Progress

- None. Stage 3 is complete; no feature implementation is in progress.

## Not Started

- Stage 4 and all later stages.
- App whitelist or per-app selection.
- Contact/group blocking and source rules.
- AI classification, tasks, events, memories, chat, tools, and reminders.

## Known Issues

- Non-blocking UI issue: Pending / Uploaded counts do not refresh in real time;
  the latest values may appear only after a page refresh or Connection Test.
  Background capture and upload are working normally. Record this as a future
  UI improvement only; no fix is included in this closeout.
- The public Funnel remains reachable. `/api/messages` stays disabled with
  HTTP 503 unless the Windows process has `ASSISTANT_MESSAGE_API_TOKEN`; an
  incorrect or missing request token returns HTTP 401.
- Ports 443 and 8443 are not supported in the current mobile network. Continue
  using external port 10000.
- The Codex environment required a temporary local dependency bridge and ASCII
  drive mapping for validation because Java TLS and Unix-domain loopback were
  restricted. Both were removed; committed Gradle configuration uses standard
  repositories and paths.

## Important Decisions

- Canonical real-PC project path: `E:\AI-Assistant-Android`.
- Canonical mobile Base URL:
  `https://pc-20260527girh.taila980ab.ts.net:10000`.
- Internal FastAPI address: `http://127.0.0.1:8000`.
- URL and token are user-entered app-private settings, not build constants.
- Android is a sensor and temporary delivery queue; Windows remains the only
  long-term business-data source.
- Room is used for durable phone-side pending state; WorkManager provides
  immediate and periodic retries.
- Stable notification IDs are deterministic UUIDs derived from package,
  notification key, and post time. Windows enforces the final idempotency
  boundary using device ID plus client message ID.
- Uploaded phone rows keep only a minimal receipt; raw facts remain in the
  Windows `messages` table.
- No historical notification query, source filtering, AI, task, reminder, or
  Stage 4 work is included.

## Next Step

Keep the accepted Stage 3 baseline. Wait for explicit user instructions before
defining or starting Stage 4. The non-blocking counter refresh improvement is
deferred and is not authorization to change functionality.

## Real Windows APK Build — 2026-09-18

- Verified clean `master` at `92a2179` before building in
  `E:\AI-Assistant-Android`.
- Built from this checkout with its Gradle 8.13 Wrapper, JDK 17.0.20.1,
  Android SDK 35, and Build Tools 35.0.0. No business source changes.
- `assembleDebug`: BUILD SUCCESSFUL. APK signature verification: passed (v2).
- APK: `E:\AI-Assistant-Android\app\build\outputs\apk\debug\app-debug.apk`.
- Package: `com.tianpl.aiassistant`; version 0.3.0; minimum API 26;
  target API 35; size 2,990,894 bytes.
- SHA-256: `06AD14242A6BCE1C60D2ED89E4513E700DB0104D4D4DC1669F36F44F39131FEA`.
- Local environment workarounds, all ignored by Git:
  - `.tools/android-sdk` is a junction to the existing SDK in the older
    `E:\AI-Assistant—Android` checkout, avoiding AAPT's non-ASCII path issue.
  - `.tools/cached-maven` and `.tools/cached-deps.init.gradle` expose existing
    dependency files for offline builds after Maven Central Java TLS failed.
  - The build sets `jdk.net.unixdomain.tmpdir` to a nonexistent directory so
    JDK local pipes fall back to TCP instead of failing Unix-domain connect.
  - Rebuild in PowerShell with `& .\.tools\build-debug.ps1`.
    This local helper still depends on the old checkout's JDK, SDK and Gradle
    cache; preserve that directory until the tools are relocated.
- Remaining non-fatal warnings: SDK XML tool-version mismatch and deprecated
  `getParcelableArray` usage. Neither blocked APK generation.
- `adb devices -l`: no attached device. This run validated packaging and
  signing; it did not rerun the earlier unit/server suites or validate phone
  behavior. Windows Stage 0-2 source and runtime configuration were untouched.
- At build time, physical-device acceptance was pending. The user subsequently
  confirmed Stage 3 completion on 2026-09-19, as recorded above. Stage 4 remains
  unstarted.

## Last Updated

2026-09-19

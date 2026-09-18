# Project Status

## Current Stage

Stage 3 — Android Notification Capture and Durable Upload (Implementation Complete; Real-Device Acceptance Pending)

## Current Goal

Validate this path on the user's Android phone and real Windows deployment:

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

- Real-device Stage 3 acceptance on the user's Android phone.
- Confirm the Windows service process has `ASSISTANT_MESSAGE_API_TOKEN` set and
  enter the matching value in the app.
- Validate real notification capture and online/offline recovery through the
  canonical Funnel endpoint.

## Not Started

- Stage 4 and all later stages.
- App whitelist or per-app selection.
- Contact/group blocking and source rules.
- AI classification, tasks, events, memories, chat, tools, and reminders.

## Known Issues

- No Android device is attached to this Codex workspace. Automated tests cover
  the A–H queue/retry requirements, but real `NotificationListenerService`,
  process-kill, network-toggle, and phone-to-Funnel behavior still require the
  user's physical phone before Stage 3 can be marked Completed.
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

Install the generated debug APK on the physical Android phone, configure the
canonical HTTPS Base URL and matching token, grant Notification Access, and
perform the real-device A–H acceptance checklist. After the user confirms all
items, mark Stage 3 Completed in `STATUS.md`. Do not begin Stage 4.

## Last Updated

2026-09-18

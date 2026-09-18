# Project Status

## Current Stage

Stage 2 — SQLite Message Storage (Completed)

## Current Goal

Preserve the validated Stage 2 SQLite message-storage baseline and wait for explicit user confirmation before entering Stage 3.

## Completed

- Read and followed `AGENTS.md`, `SYSTEM_DESIGN.md`, and `ROADMAP.md`.
- Initialized the project as a Git repository and preserved the Stage 0/1 milestone in Git.
- Completed Stage 0 connectivity validation.
- Completed Stage 1 with a minimal FastAPI application exposing only `GET /health`.
- Completed final Stage 1 acceptance on the user's real Windows PC:
  - Project directory: `E:\AI-Assistant-Android`
  - Internal FastAPI address: `http://127.0.0.1:8000`
  - Health response: `{"status":"ok","message":"PC backend is running"}`
  - Windows task `AI Assistant Backend` successfully starts the formal FastAPI service after a computer restart.
  - Tailscale starts with Windows.
  - Tailscale Funnel recovers successfully after a computer restart.
  - Canonical public Base URL: `https://pc-20260527girh.taila980ab.ts.net:10000`
  - The Android phone can access `/health` while its Tailscale connection is disabled.
  - The phone's existing VPN being enabled or disabled does not affect access.
- Confirmed that Stage 1 contains no SQLite business tables, AI logic, Android notification capture, or Stage 2 message endpoints.
- Completed Stage 2 in the repository using Python's standard-library SQLite support:
  - Runtime database path: `data/assistant.db`
  - Canonical real-PC path after synchronization: `E:\AI-Assistant-Android\data\assistant.db`
  - The database directory and table are created automatically when the server imports.
  - The runtime database and SQLite sidecar files are excluded from Git.
- Created the raw `messages` table with the required fields:
  - `id`
  - `source_app`
  - `source_type`
  - `conversation_name`
  - `conversation_type`
  - `sender`
  - `title`
  - `raw_text`
  - `received_at`
  - `created_at`
  - `device_id`
  - `notification_id`
- Added `POST /api/messages` to create and persist one raw message.
- Added `GET /api/messages` to read persisted raw messages.
- Added a Stage 2 safety gate:
  - Both message endpoints are disabled unless `ASSISTANT_MESSAGE_API_TOKEN` is configured for the server process.
  - Requests must provide the matching token in `X-Assistant-Token`.
  - This prevents the existing public Funnel from exposing the new endpoints without protection.
- Completed the Stage 2 process-level persistence test in the Codex Windows workspace:
  - Started FastAPI on an isolated loopback port.
  - Successfully saved test message `39c6ac83-e96b-4859-8b21-9803e3bbe8c3` and received HTTP 201.
  - Successfully read the same message before restart and received HTTP 200.
  - Fully stopped the FastAPI/Uvicorn process.
  - Started a new FastAPI/Uvicorn process and successfully read the same message again.
  - Confirmed the default, token-free message API returns HTTP 503.
  - Confirmed the database contains persisted test rows and all 12 required columns.

## Stage 2 Files

- Modified `server.py`: SQLite initialization, message models, security gate, and the two Stage 2 endpoints.
- Added `test_stage2.py`: runnable save/read/restart/persistence validation.
- Modified `.gitignore`: ignores `data/*.db` and SQLite sidecar files.
- Created runtime file `data/assistant.db`: local test database, intentionally ignored by Git.

## In Progress

- No work is in progress.

## Not Started

- Stage 3 and all later stages.
- Android project creation and APK work.
- Android notification capture and outbox.
- App, user, and conversation filtering controls.
- AI classification, tasks, events, memories, tools, chat, and reminders.
- Business logic implementation.

## Known Issues

- External ports 443 and 8443 timed out in the current mobile network environment and are not supported deployment addresses.
- The Funnel remains publicly reachable. The Stage 2 message endpoints therefore default to disabled and require an explicitly configured token even for local testing.
- The temporary Stage 2 token gate is not the final mobile-device authentication design.
- Stage 2 was validated in the Codex workspace `E:\AI-Assistant—Android`. These changes and the ignored test database have not been synchronized to the user's real project at `E:\AI-Assistant-Android` by this task.

## Important Decisions

- The canonical Windows project directory is `E:\AI-Assistant-Android`.
- The canonical mobile/API Base URL is `https://pc-20260527girh.taila980ab.ts.net:10000`.
- Funnel traffic terminates at the internal FastAPI service on `127.0.0.1:8000`.
- Port 10000 is the only supported external mobile port for the current deployment; do not use 443 or 8443.
- The Android client does not need to keep Tailscale connected and should use the canonical HTTPS Funnel Base URL.
- Preserve the verified Tailscale, Funnel, FastAPI, and Windows task configuration unless a later test demonstrates a problem.
- Use Python's built-in `sqlite3`; do not add an ORM or migration framework during Stage 2.
- Generate `id` and UTC `created_at` values on the Windows server.
- Store raw message facts only; do not add AI interpretations or later-stage data models.
- Keep `data/assistant.db` outside Git while preserving it on the local disk across service restarts.
- Do not begin Stage 3 without explicit user confirmation.

## Next Step

Stop after Stage 2. Synchronize the Stage 2 repository changes to the real Windows project when requested, validate them there, and wait for explicit user confirmation before beginning Stage 3. Do not expose message data through the public Funnel without an approved device-authentication design.

## Last Updated

2026-09-18

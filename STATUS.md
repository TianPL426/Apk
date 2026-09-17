# Project Status

## Current Stage

Stage 1 — Minimal Windows API Server (Complete; Awaiting Stage 2 Confirmation)

## Current Goal

Stage 1 is complete. Keep the validated FastAPI `/health` service available and wait for explicit confirmation before entering Stage 2.

## Completed

- Read `AGENTS.md`, `SYSTEM_DESIGN.md`, and `ROADMAP.md`.
- Initialized the project folder as a Git repository.
- Completed Stage 0 based on the user's end-to-end validation:
  - Tailscale is installed, signed in, and starts with Windows.
  - Tailscale Funnel is enabled and forwards to `http://127.0.0.1:8000`.
  - The public HTTPS address is `https://pc-20260527girh.taila980ab.ts.net`.
  - The Android phone successfully reached `/health` through the Funnel with its existing VPN both enabled and disabled.
  - The validated response was `{"status":"ok","message":"PC backend is running"}`.
- Located the previous test service at `C:\AI-Assistant-Test\server.py` and confirmed it used Python's standard-library `BaseHTTPServer`, not FastAPI.
- Created the Stage 1 FastAPI project in this repository:
  - `server.py` contains only `GET /health`.
  - `requirements.txt` pins FastAPI 0.141.1 and Uvicorn 0.53.0.
  - `.venv314` uses the installed system Python 3.14.5.
- Replaced the currently running port 8000 process with Uvicorn/FastAPI without changing Tailscale or Funnel.
- Validated the current service:
  - Local `GET http://127.0.0.1:8000/health` returned HTTP 200 with the expected JSON and `Server: uvicorn`.
  - Local OpenAPI contains only `/health`.
  - Funnel `GET https://pc-20260527girh.taila980ab.ts.net/health` returned HTTP 200 with the expected JSON and `Server: uvicorn`.
  - The user confirmed that the Android phone successfully reached the current FastAPI `/health` endpoint through the Funnel.
- Completed Stage 1 without adding SQLite, AI, Android notification capture, or business endpoints.

## In Progress

- No automated development work is in progress.
- The user will manually update the existing `AI Assistant Backend` boot task as a separate operational item.

## Not Started

- Stage 2 and all later stages.
- Android project creation.
- SQLite and business data endpoints.
- Business logic implementation.

## Known Issues

- The current FastAPI process is running, but the existing boot task still points to `C:\AI-Assistant-Test\server.py`.
- Updating the existing task while preserving its stored-password login failed because Windows rejected the saved credentials.
- Do not change the task to S4U and do not run it as `SYSTEM`; the backend will later require normal external network/API access.
- The repository contains `AI-Assistant—Android.lnk`, but its target is unrelated to the described Python service; it will not be executed or modified during Stage 1.
- The Funnel is publicly reachable. Stage 1 exposes only a non-sensitive health response; future business endpoints must not be exposed without authentication and an explicit security design.

## Important Decisions

- Preserve the verified Tailscale and Funnel configuration unchanged.
- The Android APK will later use the Funnel HTTPS address and does not need to keep the Tailscale client connected.
- Stage 1 is limited to a minimal FastAPI service and `GET /health`.
- Reuse the existing `AI Assistant Backend` boot task rather than creating a second competing startup entry.
- The user will manually update the existing task while preserving its current user/password-based execution mode.
- No database, AI, Android project, notification capture, or business logic will be added in Stage 1.

## Manual Startup Task

In Windows Task Scheduler, edit the existing `AI Assistant Backend` task action to use:

- Program/script: `E:\AI-Assistant—Android\.venv314\Scripts\python.exe`
- Add arguments: `-m uvicorn server:app --host 127.0.0.1 --port 8000`
- Start in: `E:\AI-Assistant—Android`

Keep the task's existing user/password-based execution mode. Do not select S4U or `SYSTEM`.

## Next Step

The user should manually update and test the existing boot task using the documented action fields. After that operational item, wait for explicit user confirmation before beginning Stage 2.

## Last Updated

2026-09-17

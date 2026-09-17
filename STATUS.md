# Project Status

## Current Stage

Stage 1 — Minimal Windows API Server (Completed)

## Current Goal

Preserve the validated Stage 1 deployment baseline and wait for explicit user confirmation before entering Stage 2.

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

## In Progress

- No work is in progress.

## Not Started

- Stage 2 and all later stages.
- Android project creation.
- SQLite and business data endpoints.
- Business logic implementation.

## Known Issues

- External ports 443 and 8443 timed out in the current mobile network environment and are not supported deployment addresses.
- The Funnel is publicly reachable. Stage 1 exposes only a non-sensitive health response; future business endpoints must not be exposed without authentication and an explicit security design.

## Important Decisions

- The canonical Windows project directory is `E:\AI-Assistant-Android`.
- The canonical mobile/API Base URL is `https://pc-20260527girh.taila980ab.ts.net:10000`.
- Funnel traffic terminates at the internal FastAPI service on `127.0.0.1:8000`.
- Port 10000 is the only supported external mobile port for the current deployment; do not use 443 or 8443.
- The Android client does not need to keep Tailscale connected and should use the canonical HTTPS Funnel Base URL.
- Preserve the verified Tailscale, Funnel, FastAPI, and Windows task configuration unless a later test demonstrates a problem.
- Do not begin Stage 2 without explicit user confirmation.

## Next Step

Wait for explicit user confirmation to begin Stage 2. Before exposing Stage 2 message endpoints through the public Funnel, decide whether they will remain local-only during testing or be protected by device authentication.

## Last Updated

2026-09-17

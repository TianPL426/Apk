# Personal AI Passive Memory Assistant — Development Roadmap

## Development Principle

Only one stage should be actively implemented at a time.

Do not begin the next stage until the current stage has been tested and confirmed.

Each stage should end with:

- a clear validation result,
- an updated `STATUS.md`,
- known issues recorded,
- and preferably a Git commit.

---

## Stage 0 — Device Connectivity

### Goal

Establish reliable connectivity between the Android phone and Windows PC.

### Main Tasks

- Install and configure Tailscale on both devices.
- Confirm both devices can see each other.
- Confirm the phone can reach the PC through the Tailscale network.

### Completion Standard

The phone can reliably reach the Windows PC through the private network.

### Do Not Implement Yet

- FastAPI business logic
- database
- AI
- Android notification capture

---

## Stage 1 — Minimal Windows API Server

### Goal

Create the smallest usable Windows-side server.

### Main Tasks

- Create a Python project.
- Add FastAPI.
- Implement `/health`.
- Run the server on Windows.
- Confirm the phone can access `/health`.

### Completion Standard

The phone receives a successful response from:

```text
GET /health
```

### Do Not Implement Yet

- SQLite business tables
- AI
- Android notification listener

---

## Stage 2 — SQLite Message Storage

### Goal

Allow the Windows server to save a test message.

### Main Tasks

- Add SQLite.
- Create the initial `messages` table.
- Add `POST /api/messages`.
- Add `GET /api/messages`.
- Save and retrieve test messages.

### Completion Standard

A test message sent to the server can be saved and queried later.

### Do Not Implement Yet

- AI classification
- tasks
- memories
- notification capture

---

## Stage 3 — Minimal Android APK

### Goal

Create an APK that can manually send a test message to the Windows server.

### Main Tasks

- Create Android project in Kotlin.
- Add server address configuration.
- Add a simple text field and send button.
- Send one test message to `POST /api/messages`.
- Display success or failure.

### Completion Standard

Typing a message into the APK results in the message appearing in the PC database.

### Do Not Implement Yet

- notification listener
- AI
- task page
- reminder system

---

## Stage 4 — Android Notification Capture

### Goal

Automatically capture available Android notifications and send them to the PC.

### Main Tasks

- Add `NotificationListenerService`.
- Request notification access from the user.
- Capture basic metadata.
- Send notifications to the Windows server.
- Handle duplicate notifications.

### Completion Standard

A real supported notification can appear in the Windows raw message database without manual input.

### Do Not Implement Yet

- AI task extraction
- automatic replies
- messaging app control

---

## Stage 5 — APK Reads PC Records

### Goal

Make the APK a viewer for computer-side records.

### Main Tasks

- Add `GET /api/messages` integration.
- Add a basic recent messages page.
- Confirm no long-term Android business database is required.

### Completion Standard

The APK can display raw records stored on the PC.

### Do Not Implement Yet

- AI classification
- task merging
- reminder intelligence

---

## Stage 6 — AI Classification

### Goal

Use AI to classify incoming messages.

### Main Tasks

- Add model configuration on Windows.
- Keep model credentials on Windows only.
- Define structured output.
- Classify into:
  - TASK
  - EVENT
  - MEMORY
  - IGNORE
- Store classification result separately from raw messages.

### Completion Standard

A test set of messages produces structured classifications while preserving the original message.

### Do Not Implement Yet

- complex long-term memory
- automatic execution

---

## Stage 7 — Formal Task, Event, and Memory Data Models

### Goal

Turn AI classifications into structured records.

### Main Tasks

- Add tables for:
  - tasks,
  - events,
  - memories,
  - people,
  - projects,
  - source relationships.
- Convert AI output into these structures.

### Completion Standard

Classified messages create valid structured records linked back to their source message.

---

## Stage 8 — Association and Merge Logic

### Goal

Prevent duplicate tasks and allow later messages to update earlier information.

### Main Tasks

- Search related existing records.
- Link follow-up messages.
- Update deadlines.
- Update instructions.
- Cancel or complete existing tasks when appropriate.
- Preserve all source links.

### Completion Standard

Multiple follow-up messages about one task update one task instead of creating duplicates.

---

## Stage 9 — APK Home Screen

### Goal

Create a useful daily overview.

### Main Tasks

Show:

- today's tasks,
- overdue tasks,
- recent important memories,
- recent events,
- recent AI-organized items.

### Completion Standard

The user can open the APK and understand what currently matters.

---

## Stage 10 — Reminder System

### Goal

Allow the Windows side to decide when the user should be reminded.

### Main Tasks

- Add reminder records.
- Add reminder scheduling logic.
- Add APK-side reminder display.
- Start with a simple and reliable delivery mechanism.
- Avoid excessive notifications.

### Completion Standard

A test task with a deadline produces a reminder on the phone.

---

## Stage 11 — AI Query Interface

### Goal

Allow the user to query their stored personal information.

### Example Queries

- What do I still need to do today?
- What did my supervisor ask me to do?
- What important information did I receive recently?
- What is due this week?

### Main Tasks

- Add `POST /api/assistant/query`.
- Retrieve relevant structured data.
- Add model response generation.
- Display answers in the APK.

### Completion Standard

The APK can ask a natural-language question and receive an answer grounded in PC records.

---

## Stage 12 — User Correction and Feedback

### Goal

Allow the user to correct AI mistakes.

### Main Tasks

Add actions such as:

- not a task,
- should be a task,
- wrong deadline,
- wrong project,
- wrong person,
- dismiss memory.

### Completion Standard

The system stores feedback and updates the affected record safely.

---

## Stage 13 — Reliability, Security, Logging, Backup

### Goal

Make the system safer and recoverable.

### Main Tasks

- authentication token,
- safer network configuration,
- server logs,
- error handling,
- database backup,
- duplicate protection,
- retry policy,
- optional temporary Android outbox,
- recovery documentation.

### Completion Standard

A temporary failure does not silently lose important data.

---

## Stage 14 — V1 Packaging

### Goal

Produce the first installable and usable release.

### Main Tasks

- clean build,
- APK packaging,
- Windows startup instructions,
- configuration instructions,
- backup instructions,
- basic troubleshooting,
- release notes.

### Completion Standard

The user can install the APK, start the Windows service, connect both devices, and use the full V1 workflow.

---

# V1 Success Definition

V1 is successful when this full path works:

```text
Phone notification
    ↓
Android APK captures it
    ↓
Message goes to Windows
    ↓
Raw message is stored
    ↓
AI classifies and organizes it
    ↓
Task / Event / Memory is updated
    ↓
APK can read the result
    ↓
User receives reminder when appropriate
```

Anything beyond this path should be treated as post-V1 work unless explicitly approved.

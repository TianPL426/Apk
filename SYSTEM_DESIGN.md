# Personal AI Passive Memory Assistant — System Design

## 1. Project Purpose

This project builds a personal AI assistant focused on **passive memory and task extraction**.

The assistant should quietly observe information that reaches the user's phone, especially notifications from applications such as WeChat and QQ, send that information to the user's Windows computer, preserve the original information, and let AI determine whether the content should become:

- a task,
- an event,
- a useful memory,
- or something to ignore.

The system is not primarily designed to automate messaging or control other apps. Its first responsibility is to **remember for the user, organize information, and remind the user when necessary**.

---

## 2. Core Product Principle

The system follows one architectural rule:

> **Phone = sensor + interface.  
> Windows PC = server + long-term memory + AI brain.**

The Android APK must remain lightweight.

The Windows computer is the only long-term business data source.

The Android APK must not become an independent long-term memory system.

---

## 3. High-Level Architecture

```text
Android Phone
│
├─ WeChat / QQ / Mail / Other notifications
│
└─ NotificationListenerService
        │
        ▼
   Android APK
        │
        │  HTTPS / API
        │  over Tailscale
        ▼
Windows PC
│
├─ FastAPI Server
├─ Raw Message Store
├─ SQLite Database
├─ AI Processing Pipeline
├─ Task / Event / Memory Logic
├─ Reminder Logic
└─ Query API
        │
        ▼
Android APK
│
├─ Today
├─ Tasks
├─ Memories
├─ Raw Messages
└─ AI Query
```

---

## 4. Android APK Responsibilities

The Android application is responsible for:

1. Obtaining Android notification access after explicit user authorization.
2. Capturing available notification content from supported applications.
3. Extracting basic metadata such as:
   - source application,
   - sender when available,
   - notification text,
   - receive time,
   - notification identifier if available.
4. Sending raw captured notification data to the Windows server.
5. Reading structured data from the Windows server.
6. Displaying:
   - tasks,
   - reminders,
   - important memories,
   - recent records,
   - AI answers.
7. Allowing the user to:
   - mark tasks complete,
   - correct wrong AI judgments,
   - search or ask questions.
8. Receiving reminder information from the Windows side.

The APK should not perform the main AI reasoning process.

The APK should not store long-term business records.

A very small temporary outbox may be added later only to prevent data loss when the PC is temporarily unreachable.

---

## 5. Windows PC Responsibilities

The Windows computer is the central system.

It is responsible for:

1. Running the API server.
2. Receiving messages from the phone.
3. Preserving raw messages.
4. Running or calling AI models.
5. Classifying incoming information.
6. Extracting structured information.
7. Linking new information with existing tasks, memories, people, and projects.
8. Updating existing records instead of creating duplicates when appropriate.
9. Maintaining the long-term database.
10. Answering questions from the APK.
11. Deciding whether reminders are required.
12. Returning structured results to the APK.
13. Maintaining logs and recovery information.

---

## 6. Communication Layer

### Initial design

Use:

- Tailscale for private device-to-device networking.
- FastAPI on the Windows PC.
- HTTPS where practical.
- Device authentication token for the APK.

The first version should avoid external cloud storage unless needed later.

The preferred communication path is:

```text
Android APK
    ↓
Tailscale
    ↓
Windows FastAPI
    ↓
SQLite / AI
```

---

## 7. Data Philosophy

The system must distinguish between **raw facts** and **AI interpretations**.

### Raw messages

Raw captured notification content must be preserved.

Example:

```json
{
  "id": "msg_000001",
  "source_app": "WeChat",
  "sender": "Teacher Wang",
  "raw_text": "Please send me the revised figure tomorrow afternoon.",
  "received_at": "2026-09-17T19:35:21+08:00"
}
```

### AI interpretation

AI may produce:

```json
{
  "type": "TASK",
  "title": "Send revised figure",
  "deadline": "2026-09-18T15:00:00+08:00",
  "person": "Teacher Wang",
  "confidence": 0.96,
  "source_message_id": "msg_000001"
}
```

AI interpretation must never overwrite or erase the original message.

---

## 8. Core AI Classification

Every incoming message should first be classified into one of four categories:

### TASK

Something the user is expected to do.

Example:

> Send me the revised figure tomorrow.

### EVENT

A time-bound event or appointment.

Example:

> Group meeting is Friday afternoon.

### MEMORY

Useful information worth preserving but not directly actionable.

Example:

> The rheometer is free Friday morning.

### IGNORE

Casual or irrelevant information.

Example:

> Haha.

---

## 9. Structured Extraction

For non-ignored messages, AI should extract when possible:

- action,
- object,
- person,
- date,
- deadline,
- location,
- project,
- priority,
- confidence,
- source message,
- relation to existing records.

---

## 10. Task State Maintenance

The system must not treat every new message as a new task.

The assistant should maintain the current real-world state.

Example conversation:

1. "Send me the rheology figure tomorrow afternoon."
2. "Figure 2 does not need changes."
3. "Refit Figure 3."
4. "No rush, next Monday is fine."

The final state should be one updated task:

```text
Task:
Revise and send the rheology figure

Requirements:
- Figure 2 unchanged
- Figure 3 must be refitted

Deadline:
Next Monday
```

The source messages should remain linked to the task.

---

## 11. Initial Database Structure

The first version should use SQLite.

Recommended tables:

### messages

Stores raw captured messages.

Suggested fields:

- id
- source_app
- sender
- raw_text
- received_at
- notification_id
- content_hash
- created_at

### tasks

Stores active and historical tasks.

Suggested fields:

- id
- title
- description
- status
- priority
- deadline
- person_id
- project_id
- created_at
- updated_at

### events

Stores appointments and time-based events.

### memories

Stores useful non-task information.

Suggested fields:

- id
- content
- memory_type
- importance
- source_message_id
- created_at

### people

Stores normalized people or contact identities.

### projects

Stores projects or contexts.

Examples:

- KGM project
- Carrageenan manuscript
- Thesis
- Personal administration

### task_sources

Maps tasks to one or more source messages.

### reminders

Stores reminder rules and reminder state.

---

## 12. Core API Direction

The exact schema may evolve, but the initial server should expose endpoints similar to:

```text
POST   /api/messages
GET    /api/messages
GET    /api/tasks
GET    /api/tasks/today
PATCH  /api/tasks/{id}
GET    /api/memories
POST   /api/assistant/query
POST   /api/feedback
GET    /health
```

---

## 13. APK Interface Scope

The first usable APK should stay simple.

Recommended bottom navigation:

```text
Home
Tasks
Memories
AI
```

The Home screen should show:

- today's tasks,
- overdue tasks,
- recent important memories,
- recent reminders,
- recent AI-organized information.

The APK should request data from the PC rather than maintaining its own long-term copy.

---

## 14. AI Query Design

The APK may allow questions such as:

- What do I still need to do today?
- What has my supervisor asked me to do recently?
- What deadlines are approaching?
- What important information did I receive this week?
- What did someone tell me about a specific experiment?

The APK sends the query to the Windows server.

The Windows server retrieves relevant records, passes structured context to the model, and returns the answer.

---

## 15. Reminder Design

The Windows side decides whether something deserves a reminder.

The first implementation may use simple periodic synchronization.

A later version may use a push mechanism to wake the APK.

Reminder logic must avoid excessive notifications.

The product should behave like a quiet secretary, not an aggressive task manager.

---

## 16. Confidence and User Correction

AI should output confidence where useful.

User correction must be supported.

Examples:

- "This is not a task."
- "This should be a task."
- "The deadline is wrong."
- "This message belongs to another project."

Corrections should be stored as feedback and may later be used to improve rules and prompts.

---

## 17. Security Principles

At minimum:

1. Do not hard-code AI API keys inside the APK.
2. Keep AI credentials on the Windows side.
3. Use private networking.
4. Authenticate phone requests.
5. Preserve raw records.
6. Avoid exposing the FastAPI service publicly without protection.
7. Keep sensitive logs minimal.
8. Prefer local storage on the user's own computer.
9. Back up the database.
10. Do not delete raw data merely because AI classified it as unimportant.

---

## 18. Non-Goals for V1

Do not implement these in the first version:

- automatic WeChat replies,
- automatic QQ replies,
- direct control of messaging apps,
- reading complete private chat databases,
- complex multi-agent architecture,
- continuous computer screen OCR,
- automatic execution of research work,
- autonomous modification of user files,
- advanced vector database infrastructure,
- cloud-scale deployment.

---

## 19. Future Expansion

Possible later extensions:

- observe Windows applications,
- connect browser activity,
- connect Word / Excel / Origin activity,
- link computer activity with messaging tasks,
- detect whether a task may already be complete,
- add richer reminders,
- local small-model filtering,
- vector search,
- project-specific Skills,
- Codex or other execution agents,
- optional automatic execution after explicit user approval.

---

## 20. Architecture Stability Rule

The following decisions are considered fixed unless the user explicitly changes them:

1. Android is primarily a sensor and interface.
2. Windows is the long-term data center and AI brain.
3. Long-term business data lives on Windows.
4. Raw messages and AI interpretations remain separate.
5. The project should be built incrementally.
6. No complex multi-agent system is required for V1.

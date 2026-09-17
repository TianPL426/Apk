# AGENTS.md

## Purpose

This file defines mandatory development rules for Codex or any other coding agent working on this repository.

These rules are more important than convenience, speed, or speculative improvements.

---

## 1. Required Reading Before Work

Before making any meaningful change, read:

1. `AGENTS.md`
2. `SYSTEM_DESIGN.md`
3. `ROADMAP.md`
4. `STATUS.md` if it exists

Then inspect the current Git status and recent relevant commits.

Do not rely on memory from a previous conversation.

---

## 2. Architecture Rules

The following architecture is fixed unless the user explicitly changes it:

- Android APK = sensor + interface.
- Windows PC = server + long-term memory + AI brain.
- Windows is the only long-term business data source.
- Main AI reasoning belongs on Windows.
- AI API keys must not be embedded in the APK.
- Raw messages and AI-generated structured records must remain separate.
- AI interpretation must never overwrite or delete raw facts.
- V1 does not require a complex multi-agent architecture.

---

## 3. Android Scope

The first-version Android APK should primarily:

- read authorized Android notifications,
- send captured data to Windows,
- query Windows records,
- display tasks and memories,
- receive/display reminders,
- provide an AI query interface.

Do not turn the Android client into the main database or main AI runtime.

A temporary unsent-message outbox may be introduced later if required for reliability.

---

## 4. Windows Scope

Windows is responsible for:

- FastAPI server,
- SQLite,
- raw message storage,
- AI classification,
- structured extraction,
- task/event/memory management,
- association and merge logic,
- reminders,
- query processing,
- logs,
- backups.

---

## 5. Stage Discipline

Work on only the current stage defined by `STATUS.md`.

If `STATUS.md` does not yet exist, do not assume progress.

Do not implement future-stage features "while already here."

Do not perform speculative refactors outside the current task.

The current stage must be validated before the next stage begins.

---

## 6. Small-Task Rule

Prefer small, verifiable changes.

A task should have:

- one clear objective,
- a small implementation scope,
- an explicit validation method,
- a clear stop condition.

Avoid combining unrelated work into one change.

---

## 7. Testing Rule

After modifying code:

- run the most relevant available tests,
- run a direct validation when tests do not exist,
- report what was actually tested,
- report anything that could not be tested.

Never claim success without evidence.

---

## 8. STATUS.md Rule

At the end of a meaningful development step, update `STATUS.md`.

It should contain at least:

- Current Stage
- Current Goal
- Completed
- In Progress
- Not Started
- Known Issues
- Important Decisions
- Next Step
- Last Updated

Do not mark a stage complete until its completion standard has been validated.

---

## 9. Interruption / Resume Rule

If work stops because of:

- usage limits,
- session interruption,
- restart,
- model change,
- context loss,
- or a new Codex session,

then before continuing:

1. read the required project files,
2. inspect `STATUS.md`,
3. inspect Git status,
4. inspect recent commits,
5. continue only from the documented next step.

Never reconstruct project state from vague memory.

---

## 10. Git Rule

Use Git as the recovery history.

Prefer a commit after each completed stage or other stable milestone.

Before large changes:

- inspect current changes,
- preserve a known-good state,
- avoid mixing unrelated modifications.

If the code and documented architecture disagree, report the mismatch before deciding how to fix it.

---

## 11. Security Rule

Never hard-code:

- AI API keys,
- authentication secrets,
- private tokens,
- passwords.

Use environment variables or local configuration excluded from version control.

Do not expose the server publicly without explicit security design.

---

## 12. Data Integrity Rule

Raw captured information is evidence.

Structured AI output is interpretation.

Therefore:

- preserve source records,
- link derived records to sources,
- do not silently rewrite history,
- do not delete raw data because a model classified it as irrelevant,
- make updates auditable where practical.

---

## 13. No Unapproved Scope Expansion

Do not add these unless explicitly requested:

- multi-agent orchestration,
- automatic WeChat or QQ replies,
- private chat database extraction,
- desktop screen surveillance,
- vector database infrastructure,
- cloud deployment,
- autonomous file editing,
- autonomous execution of user work.

---

## 14. Conflict Priority

If instructions conflict, use this priority:

1. explicit latest user instruction,
2. `AGENTS.md`,
3. `SYSTEM_DESIGN.md`,
4. `ROADMAP.md`,
5. `STATUS.md` for current progress state,
6. existing implementation details.

If a conflict would change architecture, stop and report it.

---

## 15. Definition of Done for a Stage

A stage is complete only when:

- its required implementation exists,
- the documented completion standard is validated,
- known issues are recorded,
- `STATUS.md` is updated,
- the next step is clearly written,
- and the repository remains in a coherent state.

---

## 16. Communication Style

When reporting work:

- be concise,
- state what changed,
- state what was tested,
- state what remains,
- mention blocking issues,
- do not bury failures.

The user should always be able to tell whether the current stage actually works.

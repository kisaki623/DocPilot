# DocPilot Docs Index

This file is the entry point for the `docs` directory. It tells new Codex sessions which documents to read first, which files are historical, and where the current RAG direction lives.

## 1. New Codex Session: Read First

Read these files before scanning older logs:

- `docs/ai-dev/STATE.md` - current project state and boundaries.
- `docs/ai-dev/CURRENT_TASK.md` - the active task for the next implementation round.
- `docs/ai-dev/ROADMAP_RAG.md` - long-term RAG roadmap.
- `docs/ai-dev/DECISIONS.md` - compact ADR decisions.
- `docs/ai-dev/CONSTRAINTS.md` - collaboration and safety constraints.
- `docs/ai-dev/PROGRESS_LOG.md` - short progress log.

Rules:

- Do not default-read `docs/CHANGELOG_CODING.md`, `docs/TODO_NEXT.md`, or `docs/CODEX_HANDOFF.md`.
- Read those large historical files only when tracing why a decision was made.
- The current task is defined by `docs/ai-dev/CURRENT_TASK.md`.
- The long-term RAG direction is defined by `docs/ai-dev/ROADMAP_RAG.md`.
- If documentation conflicts with code, tests, or runnable results, use code, tests, and runnable results as the source of truth.

## 2. Interview And Job Materials

Use these when preparing resumes, interviews, or project demos:

- `docs/PROJECT_INTERVIEW_BRIEF.md`
- `docs/RESUME_BULLETS.md`
- `docs/interview/`

These files should keep the public story concise. Do not put fake embedding, in-memory vector store, blocked runtime, or similar internal boundaries in the first part of outward-facing material. Keep those boundaries in internal `ai-dev` docs or later clarification sections.

## 3. RAG Design Reference

Use these as reference material for implementation details and historical design context:

- `docs/RAG_MINIMAL_DESIGN.md`
- `docs/VECTOR_STORE_SELECTION.md`
- `docs/RAG_VECTOR_STORE_ADAPTER_DESIGN.md`
- `docs/RAG_QDRANT_REVIEW_NOTES.md`

Current RAG planning should be written to `docs/ai-dev/ROADMAP_RAG.md`, not scattered across older design notes.

## 4. Agent Design Reference

Use these for Agent design history and specific boundaries:

- `docs/agent-upgrade-roadmap.md`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/PROMPT_ENGINEERING_NOTES.md`

These are reference docs. They should not override `docs/ai-dev/STATE.md` or `docs/ai-dev/CURRENT_TASK.md`.

## 5. Historical And Large Files

These files are retained for traceability but are not recommended for default reading:

- `docs/CHANGELOG_CODING.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`

Only read them when you need detailed historical context, old task records, or handoff notes. Do not let old TODO entries override the current route in `docs/ai-dev/CURRENT_TASK.md` and `docs/ai-dev/ROADMAP_RAG.md`.

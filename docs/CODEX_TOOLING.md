# Codex Tooling Boundary

This document records local collaboration tools and MCP capabilities available around DocPilot. It describes capability, intended use, usage conditions, and forbidden actions only. Do not store server IPs, usernames, passwords, tokens, API keys, or `.env` contents here.

## General Rules

- Prefer local repository evidence over memory.
- Do not use tools to bypass user approval, repository rules, or Git hygiene.
- Do not print secrets or raw sensitive values in reports. If a risk is found, report file path, key name, and risk type only.
- Do not execute `git add`, `git commit`, or `git push` unless the user explicitly asks for it.
- If a tool starts a local process, the agent that started it is responsible for cleanup and must report port/process state.

## Subagents

### code-map.toml

- Purpose: code map, module location, dependency/call-chain analysis, finding implementation entry points.
- Use when: a task needs understanding of backend/frontend structure, API flow, service boundaries, or where to make a change.
- Conditions: read-only by default unless explicitly assigned a bounded edit task.
- Forbidden: broad rewrites, speculative architecture changes, or editing outside the assigned scope.

### docs-research.toml

- Purpose: documentation research, README/docs consistency checks, and framework/library usage notes.
- Use when: docs need truth alignment, external documentation must be checked, or public wording needs risk control.
- Conditions: prefer official docs and repository evidence.
- Forbidden: inventing features, overstating roadmap items as implemented, or copying unverified claims into public docs.

### hk-ops.toml

- Purpose: remote server, middleware, Docker, deployment environment inspection and troubleshooting.
- Use when: cloud middleware, remote Docker, deployment, or server connectivity blocks local work.
- Required approval: before any remote access, explain the purpose, exact command category, whether it is read-only, and wait for explicit user confirmation.
- Forbidden: remote destructive operations, credential disclosure, changing server state without approval, or using hk-ops for convenience when local evidence is enough.

### risk-review.toml

- Purpose: sensitive information checks, configuration leakage review, scope/permission risk, commit-readiness review.
- Use when: before public commit, before changing config/env files, after touching docs with operational details, or when `.env`/cloud information is involved.
- Conditions: report risks with redaction.
- Forbidden: printing real secrets, deleting user files, or auto-fixing outside the requested scope.

### test-audit.toml

- Purpose: test strategy, eval/benchmark credibility, CI/verification command audit, regression coverage review.
- Use when: deciding what to run, validating claims, reviewing eval gate thresholds, or checking whether a task can be marked DONE.
- Conditions: distinguish real executed results from recommended commands.
- Forbidden: marking tasks DONE without real verification or inventing test output.

### ui-check.toml

- Purpose: frontend page review, Playwright UI checks, user-flow verification, visual regression observations.
- Use when: changes affect pages, layout, browser behavior, SSE/streaming UX, login/upload/document/Agent flows.
- Conditions: prefer browser-level evidence when UI behavior matters.
- Forbidden: relying only on screenshots or DOM assumptions when a real flow is required; starting long-running dev servers without clear ownership and cleanup.

## MCP Tools

### context7 MCP

- Purpose: query official/framework/library documentation and examples.
- Use when: implementation depends on current library behavior or official API usage, especially for frontend/backend framework details.
- Conditions: resolve the library first, then query focused docs; prefer official documentation over memory.
- Forbidden: using it to justify unsupported claims or replacing repository evidence with generic docs.

### playwright MCP

- Purpose: browser automation, frontend smoke tests, UI/E2E checks, page snapshots, interaction verification.
- Use when: a task requires real browser validation, visual behavior checks, login/upload/document/Agent flows, or SSE UI verification.
- Conditions: do not start a long-running dev server unless the user explicitly confirms or the task already authorizes it; if a server is started, track and clean it up.
- Forbidden: leaving dev servers or browser helper processes running; using Playwright to mutate persistent app data unless the task requires it; treating visual checks as backend test substitutes.

## Recommended Usage Pattern

1. Read `AGENTS.md`, `docs/TODO_NEXT.md`, `docs/CODEX_HANDOFF.md`, and `docs/CHANGELOG_CODING.md` first.
2. Use `code-map` for where/how questions.
3. Use `risk-review` before public-facing or config changes.
4. Use `test-audit` before claiming verification or DONE.
5. Use `ui-check` and Playwright MCP for browser-visible behavior.
6. Use `docs-research` or context7 only when documentation evidence is needed.
7. Use `hk-ops` only with explicit user approval for remote/server work.

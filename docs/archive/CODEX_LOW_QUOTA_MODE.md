# Codex Low Quota Mode

This guide defines a small, bounded working mode for moments when Codex or
ChatGPT agentic usage quota is low. The goal is to spend remaining quota on
high-signal, low-risk tasks instead of long-running validation or broad
implementation work.

## Good Low Quota Tasks

Use low quota mode for tasks that are quick to inspect, edit, and verify:

- Read-only summaries of current project state.
- Small Markdown edits.
- `git status --short` / `git diff` checks.
- Single-file small patches.
- README, resume bullet, or demo script wording tweaks.

## Avoid In Low Quota Mode

Do not use low quota mode for tasks that can expand into long-running work:

- Starting backend or frontend services.
- Starting middleware.
- Long-running log tailing.
- Cross-module refactors.
- Real embedding, Qdrant, Redis Vector, or LangChain4j integration.
- Editing `docker-compose`, YAML config, or dependency versions.
- Running full test suites or end-to-end tests.

## Prompt Template

```text
当前是低额度模式，只允许只读分析或单文件小改，不要启动服务，不要运行测试，不要改配置，完成后 git status --short。
```

## Stop-Loss Rules

- Keep each task within 5 to 10 minutes.
- Try the same issue at most once.
- If the task requires starting services or cross-module changes, stop
  immediately and report the blocker.

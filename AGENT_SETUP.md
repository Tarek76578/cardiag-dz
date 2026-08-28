# CarDiag autonomous agent

The repository now contains:
- `AGENTS.md`: persistent engineering rules.
- `.github/workflows/cardiag-agent-safe.yml`: scheduled/manual Codex engineering loop.
- `agent-state.md`: persistent milestone state.

Required repository secret: `OPENAI_API_KEY`.

The workflow is intentionally sandboxed with `workspace-write` and only commits validated changes. It runs hourly and can also be started manually from GitHub Actions.

# Eventra
Volunteer allocation for college events. A deterministic solver decides assignments; an LLM agent orchestrates and explains; humans approve.

| Folder | Stack | Owner |
|---|---|---|
| backend/ | Java, Spring Boot | Backend dev |
| frontend/ | React | Frontend dev |
| database/ | PostgreSQL, Flyway | Database dev |
| ai-agent/ | LLM agent | AI dev |
| evaluation/ | Benchmarks (shared by backend + AI) | Backend + AI |

## Local setup
1. `cp .env.example .env`
2. `docker compose up -d`
3. Run backend and frontend (see each folder's README)

## Workflow
- `main` is protected: PRs only, 1 review, CI must pass.
- Short-lived branches: `feature/<stack>-<desc>` e.g. `feature/backend-solver`.
- Only edit your own folder; cross-folder changes need a PR review from that owner.
- Agree on `docs/api.md` before integrating.

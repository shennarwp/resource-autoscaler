# AGENTS.md

Guidance for AI agents and contributors working in this repository.

## Project overview

`resource-autoscaler` is a full-stack Azure resource autoscaler:

- **Backend**: Java 25 + Spring Boot (Maven), exposes REST APIs for metrics,
  recommendations, and cost analysis.
- **Frontend**: React + TypeScript + Vite (in `frontend/`), charts rendered with
  `recharts`.

## Repository layout

- `src/main/java/...` — Spring Boot backend (`com.resourceautoscaler.*`).
- `src/test/java/...` — backend tests.
- `frontend/` — Vite + React + TypeScript app.
- `README.md` — project docs (including Azure Log Analytics / insights setup).

## Commands

Always run these from the directory of the respective module.

### Frontend (`frontend/`)

```bash
npm install          # install dependencies
npm run dev          # start Vite dev server
npm run build        # typecheck (tsc -b) + production build
npm run lint         # oxlint
```

### Backend (repo root)

```bash
mvn test             # run unit tests
mvn package          # build the jar
```

Run the backend (insights profile, live Azure Log Analytics):

```bash
java -jar target/resource-autoscaler-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=insights --server.port=8080
```

Profiles:
- `mock` (default) — deterministic generated data, no cloud access needed.
- `insights` — reads real container metrics from Azure Monitor Container
  Insights (Log Analytics). Requires `AZURE_*` env vars (see `.env.example`).

## Verification checklist

After making changes, verify with:

1. `cd frontend && npm run lint`
2. `cd frontend && npm run build`
3. `cd <root> && mvn test`

## Frontend conventions

- `ResourceDetailPage.tsx` — resource detail view with CPU/memory charts and
  time-range selector.
- Time ranges are keyed by duration in **days** (`TIME_RANGES`). Each range has
  a corresponding x-axis label cadence in `LABEL_EVERY_MINUTES`.
- Chart data points are sorted by time before rendering.
- Shared API fetch hooks live in `frontend/src/hooks/useApi.ts`.

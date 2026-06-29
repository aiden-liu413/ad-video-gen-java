# Repository Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot + React workspace for **AIVision Control**. Backend code lives in `src/main/java/com/volcengine/demo/advideo/`, with key packages for `agent`, `client`, `controller`, `dto`, `entity`, `orchestrator`, `repository`, and `service`. Prompt assets are stored under `src/main/resources/prompts/`, and application configuration is in `src/main/resources/application.yml`. Backend tests are in `src/test/java/`. The frontend is a Vite app in `frontend/`; most UI logic is in `frontend/src/main.tsx`, with global styling in `frontend/src/styles.css`.

## Build, Test, and Development Commands

- `mvn spring-boot:run`: run the backend locally on the configured Spring Boot port.
- `cd frontend && npm run dev`: start the Vite dev server, usually at `http://localhost:8002/`.
- `cd frontend && npm run build`: type-check and build the frontend.
- `mvn test`: run backend tests.
- `mvn -Dtest=VideoTaskWorkflowFunctionalTests test`: run the main workflow functional test.
- `mvn clean package`: build the full application and package frontend assets into the Spring Boot artifact.

## Coding Style & Naming Conventions

Use Java 17 and existing Spring Boot patterns. Keep orchestration logic centralized in `WorkflowOrchestratorServiceImpl` unless a clear service boundary already exists. Use TypeScript/React conventions already present in `frontend/src/main.tsx`; avoid introducing new UI libraries or state managers without a strong reason. New or modified important methods should include Chinese comments describing function, parameters, return value, and possible exceptions.

## Testing Guidelines

Backend tests use Spring Boot Test and JUnit. Add or update tests when changing workflow behavior, selection ordering, regeneration, storage, or API contracts. Prefer focused functional tests for orchestration changes. For frontend-only layout work, run `npm run build`; add tests only if frontend test infrastructure is introduced.

## Commit & Pull Request Guidelines

Follow `git规范.md`: use `<type>(<scope>): <subject>`, for example `fix(frontend): 修复视频审核布局` or `docs: 更新项目说明`. Keep commits scoped to one concern and avoid vague subjects like `update`. Pull requests should include a concise summary, verification commands, screenshots for UI changes, linked issues when available, and notes about configuration or migration impact.

## Security & Configuration Tips

Do not commit local data, generated artifacts, secrets, or object-storage contents such as `docker-data/`, `docker/docker-data/`, `target/`, or `frontend/dist/`. Use S3-compatible configuration variables for RustFS/MinIO instead of hard-coding storage middleware details.

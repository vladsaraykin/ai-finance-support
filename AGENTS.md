# Repository Guidelines

## Project Structure & Module Organization

The MVP is split into `backend/` (Java/Spring Boot, PostgreSQL migrations under `src/main/resources/db/migration`) and `frontend/` (React/Vite components and styles under `src/`). The roadmap is in `docs/implementation-plan.md`; local PostgreSQL runs from the root `compose.yaml`. Keep tests beside each module's standard test directory, documentation in `docs/`, and generated output/dependency caches out of source control.

## Application Architecture & Roadmap

The MVP has a Java/Spring Boot REST API, PostgreSQL as the source of truth for financial records, and a separate React frontend. Text parsing uses limited local rules by default; OpenAI structured extraction is optional. Document RAG uses a LAN-hosted Infinity inference service and Qdrant; Spring handles chunking, owner-scoped indexing/search, reranking, and OpenAI answer generation. Financial facts remain in PostgreSQL. MCP and full tool-calling orchestration are roadmap work.

When the AI layer is added, it should interpret natural-language requests and call a small set of business tools, such as `record_transactions`, `search_transactions`, and `get_financial_report`. MCP tools must delegate to application services; never expose arbitrary SQL to the model. The authenticated server context must determine the user for every operation. Validate and confirm ambiguous or risky changes, and make message processing idempotent to prevent duplicate entries. The current MVP is local and single-user; do not expose it publicly before authentication is implemented.

Store transactions, categories, budgets, and goals as structured data. Calculate totals and period comparisons in PostgreSQL, not in the LLM or RAG. Use exact money representations (`BigDecimal`/`NUMERIC` or integer minor units); transfers between a user's own accounts must not count as income or expense. Use RAG with a vector index such as pgvector only for unstructured personal rules, plans, and documents, with user-scoped retrieval. Keep chat history separate from financial facts and the knowledge index.

## Build, Test, and Development Commands

Run `docker compose up -d postgres` to start the local database, `cd backend && mvn spring-boot:run` for the API, and `cd frontend && npm install && npm run dev` for the UI. Build with `cd backend && mvn package -DskipTests` and `cd frontend && npm run build`. Document changes to commands in `README.md` and keep setup reproducible from a clean checkout.

## Coding Style & Naming Conventions

Follow the formatter and lint rules adopted by the chosen language and commit their configuration with the code. Use consistent indentation, descriptive names, and small modules with clear responsibilities. Name test files to match the code under test (for example, `AccountServiceTest`) and keep configuration examples free of real credentials. Do not introduce a formatter or style convention that conflicts with existing project configuration.

## Testing Guidelines

There is no test framework or coverage requirement configured yet. Add automated tests with each behavior change, place them in the framework’s standard test directory, and document the command contributors should run. Tests should be deterministic and should not require production credentials or external services unless those dependencies are explicitly provisioned for the test environment.

## Commit & Pull Request Guidelines

No Git history is available here to establish a commit convention. Use short, imperative commit subjects that describe one change (for example, `Add account summary endpoint`). Pull requests should explain the user-visible change, note relevant validation commands and results, and link related issues. Include screenshots for UI changes and call out configuration or migration steps when applicable.

## Security & Configuration Tips

Keep secrets out of source control. Provide safe example configuration with placeholders, and document required environment variables without publishing their values. Review dependency and configuration changes for unintended exposure of financial or personal data.

## Project Skills

For backend work, follow [`skills/java-spring-project/SKILL.md`](skills/java-spring-project/SKILL.md). For React JavaScript UI work, follow [`skills/react-js/SKILL.md`](skills/react-js/SKILL.md). For test planning, implementation, or validation, follow [`skills/application-testing/SKILL.md`](skills/application-testing/SKILL.md). Read the applicable guide before making changes and preserve the existing project conventions.

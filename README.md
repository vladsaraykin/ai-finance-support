# Личный финансовый помощник

MVP для фиксации доходов и расходов, исправления записей и просмотра отчёта за месяц. Архитектура проекта и следующие этапы описаны в [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Требования

- Java 21 и Maven 3.9+
- Node.js 20+
- Docker Compose

## Локальный запуск

1. Запустите PostgreSQL: `docker compose up -d postgres`.
2. В первом терминале запустите backend: `cd backend && mvn spring-boot:run`.
3. В другом терминале установите frontend зависимости и запустите Vite: `cd frontend && npm install && npm run dev`.
4. Откройте адрес Vite, указанный в терминале.

Backend по умолчанию слушает `http://localhost:8080`, React dev server — `http://localhost:5173`.

## Сценарии и API MVP

Введите, например, `Вчера потратил 800 рублей на такси и 450 на обед`. Backend вернёт черновики, frontend даст проверить и поправить дату, категорию и место, после чего сохранит их по подтверждению.

- `POST /api/transactions/parse` — разобрать русский текст в черновики.
- `POST /api/transactions` — подтвердить и сохранить до 20 операций; поддерживает заголовок `Idempotency-Key`.
- `GET /api/transactions?month=2026-10` — список за месяц.
- `PUT /api/transactions/{id}` — исправить операцию.
- `GET /api/reports/monthly?month=2026-10` — SQL-отчёт по доходам, расходам и категориям.

Для production build используйте `cd backend && mvn package -DskipTests` и `cd frontend && npm run build`.

RAG-компоненты (Infinity embedding/reranker и Qdrant) запускаются отдельно на машине в LAN. Настройка Windows Docker Compose и проверка GPU описаны в [`rag/README.md`](rag/README.md). Текстовые документы можно индексировать через `POST /api/knowledge/documents`, задавать вопросы с источниками через `POST /api/assistant/chat`.

Для backend доступны `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `SERVER_PORT`, `SERVER_ADDRESS`, `FINANCE_OWNER_ID` и `FINANCE_TIMEZONE`. По умолчанию API привязан к `127.0.0.1`; Flyway автоматически применяет миграции при запуске. Для удалённой PostgreSQL на `45.151.31.104:5432` сначала создайте роль и базу по инструкции в [`docs/database-setup.md`](docs/database-setup.md), затем задайте соответствующие `DATABASE_URL`, `DATABASE_USERNAME` и `DATABASE_PASSWORD` в окружении backend.

По умолчанию текст разбирается локально. Чтобы включить структурированный разбор через OpenAI Responses API, задайте `AI_ENABLED=true` и `OPENAI_API_KEY`; модель по умолчанию — `gpt-5.6-sol`, её можно изменить через `OPENAI_MODEL`, а адрес API — через `OPENAI_BASE_URL`. Доступ к модели должен быть включён в вашей OpenAI API-организации. При включённом AI текст записи передаётся OpenAI — интерфейс показывает это до отправки. Результат всегда остаётся черновиком до подтверждения пользователя. Сборка приложения не вызывает внешний AI API.

## Ограничения MVP

По умолчанию распознавание основано на простых русскоязычных правилах; дополнительно доступен опциональный OpenAI-разбор со структурированным ответом. RAG индексирует текстовые правила, финансовые документы и сведения о магазинах/товарах через локальные Infinity и Qdrant; загрузка PDF пока не реализована. Поддерживаются суммы в RUB и базовые категории. MCP-инструменты и полноценная многопользовательская аутентификация остаются следующими этапами. В текущей версии один локальный владелец данных из `FINANCE_OWNER_ID`; не публикуйте этот MVP в интернет и не используйте для хранения реальных финансовых данных до добавления аутентификации и проверки безопасности.

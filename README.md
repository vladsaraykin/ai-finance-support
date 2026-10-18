# Личный финансовый помощник

MVP для фиксации доходов и расходов, исправления записей и просмотра отчёта за месяц. Архитектура проекта и следующие этапы описаны в [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Требования

- Java 21 и Maven 3.9+
- Node.js 22.12+ (или Node.js 24)
- Docker Compose

## Локальный запуск

1. Запустите PostgreSQL: `docker compose up -d postgres`.
2. Создайте первый аккаунт: `mvn -f backend/pom.xml package`, затем `./scripts/create-user.sh`. Введите пароль скрыто; по умолчанию создаётся логин `vlad13reg`. Для БД на ВМ сначала экспортируйте её реквизиты и запустите SSH-туннель, как описано в [`docs/authentication.md`](docs/authentication.md).
3. В первом терминале запустите backend: `cd backend && mvn spring-boot:run`.
4. В другом терминале установите frontend зависимости и запустите Vite: `cd frontend && npm ci && npm run dev`.
5. Откройте адрес Vite, указанный в терминале, и войдите.

Backend по умолчанию слушает `http://localhost:8080`, React dev server — `http://localhost:5173`.

## Сценарии и API MVP

Введите, например, `Вчера потратил 800 рублей на такси и 450 на обед`. Backend вернёт черновики, frontend даст проверить и поправить дату, категорию и место, после чего сохранит их по подтверждению.

- `POST /api/transactions/parse` — разобрать русский текст в черновики.
- `POST /api/transactions` — подтвердить и сохранить до 20 операций; поддерживает заголовок `Idempotency-Key`.
- `GET /api/transactions?month=2026-10` — список за месяц; недавно добавленные записи идут первыми, даже если трата была раньше.
- `PUT /api/transactions/{id}` — исправить сумму, дату, категорию, описание, магазин и способ оплаты.
- `DELETE /api/transactions/{id}` — удалить операцию из списка и отчётов, сохранив ключ импорта.
- `GET /api/reports/monthly?month=2026-10` — SQL-отчёт по доходам, расходам и категориям.

Все финансовые API требуют входа; изменяющие запросы также требуют CSRF-токен. Вход, выход и создание аккаунтов описаны в [`docs/authentication.md`](docs/authentication.md).

Для production build используйте `cd backend && mvn package -DskipTests` и `cd frontend && npm run build`.

RAG работает через OpenAI Vector Stores: облачные embeddings, поиск и встроенный reranking. Локальные модели и Qdrant не требуются. Создание хранилища, переменные `RAG_ENABLED`, `OPENAI_API_KEY`, `OPENAI_VECTOR_STORE_ID` и миграция описаны в [`rag/README.md`](rag/README.md). Пример переменных — [`.env.example`](.env.example); Spring Boot не загружает `.env` автоматически. `POST /api/knowledge/documents` загружает текст и возвращает статус индексации; `GET /api/knowledge/documents/{fileId}` проверяет готовность; `POST /api/assistant/chat` отвечает с найденными источниками.

Для backend доступны `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `SERVER_PORT`, `SERVER_ADDRESS`, `FINANCE_OWNER_ID` и `FINANCE_TIMEZONE`. По умолчанию API привязан к `127.0.0.1`; Flyway автоматически применяет миграции при запуске. Для PostgreSQL на ВМ `cave` используйте [`docs/database-setup.md`](docs/database-setup.md): скрипт создаёт БД и пользователя, сохраняет реквизиты и проверяет подключение. Для разработки с ноутбука предусмотрен SSH-туннель. Существующая внешняя БД автоматически не переносится.

По умолчанию текст разбирается локально. Чтобы включить структурированный разбор через OpenAI Responses API, задайте `AI_ENABLED=true` и `OPENAI_API_KEY`; модель по умолчанию — `gpt-5.6-sol`, её можно изменить через `OPENAI_MODEL`, а адрес API — через `OPENAI_BASE_URL`. Доступ к модели должен быть включён в вашей OpenAI API-организации. При включённом AI текст записи передаётся OpenAI — интерфейс показывает это до отправки. Результат всегда остаётся черновиком до подтверждения пользователя. Сборка приложения не вызывает внешний AI API.

## Ограничения MVP

По умолчанию распознавание основано на простых русскоязычных правилах; дополнительно доступен опциональный OpenAI-разбор со структурированным ответом. RAG передаёт и сохраняет текстовые правила, финансовые документы и сведения о магазинах/товарах в OpenAI Vector Stores; загрузка PDF пока не реализована. Документ доступен для поиска после статуса `completed`. Классификация транзакций с RAG и запоминание исправлений ещё не реализованы. Поддерживаются суммы в RUB, базовые и пользовательские категории. Вход работает по логину/паролю; аккаунты создаются оператором. Каждая сессия имеет своего владельца операций и RAG. `FINANCE_OWNER_ID` задаёт UUID первого аккаунта при создании, а запросы используют UUID из сессии. MCP-инструменты, восстановление пароля и публичная регистрация остаются следующими этапами. Размещение с systemd, временным HTTP по IP на порту 80 и последующим включением HTTPS описано в [`docs/deployment.md`](docs/deployment.md).

## Проверки

`cd backend && mvn test` — JUnit/MockMvc проверки сессий, CSRF, владельцев, валидации и OpenAI без внешних сервисов. `cd frontend && npm test` — проверки API-клиента и дат; `npm run build` — сборка React. Тест настоящей PostgreSQL включается через `TEST_DATABASE_URL`, как описано в руководстве по деплою.

История категорий из CSV подготавливается и загружается через `python3 scripts/import-rag-history.py data/import/google-operations.csv --upload`; предварительный просмотр выполняется без `--upload`. Формат, повторная загрузка и границы импорта описаны в [`docs/knowledge-import.md`](docs/knowledge-import.md). Персональные снимки и реквизиты в `data/import/` исключены из Git. Проверки импортера: `python3 -B -m unittest discover -s scripts/tests -v`.

Разовый перенос самих операций в PostgreSQL: `python3 scripts/import-transactions-once.py data/import/google-operations.csv --source-id google-finance-operations` — проверка; добавьте `--apply` для записи после применения миграций backend. Порядок подключения и защита от дублей описаны в [`docs/transactions-import.md`](docs/transactions-import.md).

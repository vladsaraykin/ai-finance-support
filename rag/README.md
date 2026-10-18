# RAG через OpenAI Vector Stores

OpenAI хранит документы, разбивает их на фрагменты, создаёт embeddings и выполняет поиск со встроенным reranking (`ranker: auto`). Spring явно вызывает поиск перед генерацией ответа через Responses API. Локальные Infinity, Qdrant и GPU больше не нужны.

## Подготовка

1. Создайте API key в OpenAI Project с доступом к Files, Vector Stores и Responses. Модель из `OPENAI_MODEL` должна быть доступна этому проекту. Для работы RAG нужен полный OpenAI API; совместимости только с chat completions или embeddings недостаточно.
2. Создайте один постоянный Vector Store в том же проекте через OpenAI dashboard или API. Скопируйте его ID вида `vs_…` в `OPENAI_VECTOR_STORE_ID`. Не создавайте новый store при каждом запуске backend.
3. Скопируйте корневой `.env.example` в `.env`, заполните ключ и ID. Не добавляйте `.env` в Git. Идентификатор модели в примере сохранён из предыдущей конфигурации; при необходимости задайте доступный вашему проекту ID.

Пример создания хранилища (ключ предварительно задан в окружении):

```sh
curl --fail-with-body https://api.openai.com/v1/vector_stores \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H 'Content-Type: application/json' \
  -H 'OpenAI-Beta: assistants=v2' \
  -d '{"name":"finance-knowledge"}'
```

Из корня репозитория загрузите **свой доверенный** `.env` в окружение процесса:

```sh
set -a
. ./.env
set +a
cd backend
mvn spring-boot:run
```

Spring Boot сам `.env` не читает; для запуска из IDE задайте эти переменные в Run Configuration. `AI_ENABLED` управляет генерацией ответов и разбором операций; `RAG_ENABLED` — загрузкой и поиском документов. Ключ и `OPENAI_BASE_URL` общие. `RAG_RESULT_COUNT` — 1–50 результатов, `RAG_SCORE_THRESHOLD` — 0–1; score не является вероятностью правильной категории.

## Загрузка и готовность

Форма «Добавить материал» передаёт текст **в OpenAI, где он сохраняется**. Поддерживается текстовый ввод до 100 000 символов; загрузка бинарных PDF/DOCX через интерфейс пока не реализована.

- `POST /api/knowledge/documents` принимает `title`, `source`, `documentType`, `text` и возвращает HTTP 202 с `documentId`, `fileId`, `status`, `error`.
- Spring загружает UTF-8 `.txt` через Files API (`purpose: assistants`) и прикрепляет его к настроенному Vector Store с атрибутами владельца, типа и источника.
- `GET /api/knowledge/documents/{fileId}` возвращает статус: `in_progress`, `completed`, `failed`, `cancelled`. Только `completed` означает готовность к поиску. Число локальных chunks больше не возвращается.
- UI проверяет статус ограниченное время. При задержке или сетевой ошибке доступна кнопка «Проверить статус» без повторной загрузки. После обновления страницы статус можно проверить через GET по сохранённому `fileId`; список документов и восстановление UI-загрузки пока не реализованы.
- `POST /api/assistant/chat` ищет документы с фильтром по серверному `FINANCE_OWNER_ID`, вызывает встроенный reranker и передаёт найденные выдержки модели. Чужие или не содержащие owner_id результаты дополнительно отбрасываются сервером.

Типы материалов: `RULE`, `FINANCIAL_GUIDE`, `STORE_CATALOG`, `PRODUCT_CATALOG`, `OTHER`. Файлы, добавленные вручную в dashboard без нужных атрибутов, не попадут в поиск приложения. При ошибке прикрепления backend пытается удалить только файл текущего запроса; если очистка не удалась, его ID записывается в журнал для ручной очистки. Сетевой обрыв во время самого upload может оставить файл, ID которого приложение не получило; автоматические повторы загрузки не выполняются.

## Миграция с локального RAG

Старые переменные `RAG_INFERENCE_*`, `RAG_QDRANT_*`, `RAG_COLLECTION`, `RAG_EMBEDDING_MODEL`, `RAG_RERANKER_MODEL`, `RAG_VECTOR_SIZE`, `RAG_CANDIDATE_COUNT` больше не используются. Compose локального RAG удалён из проекта; ранее запущенные контейнеры и тома автоматически не останавливаются и не удаляются.

Сохраните исходные документы и повторно загрузите их через приложение в OpenAI. Векторы Qdrant не переносятся. Если сохранились только фрагменты в Qdrant, сначала экспортируйте их и проверьте восстановленный текст. После проверки новых ответов старые сервисы можно остановить. Не удаляйте старые данные до сверки.

## Границы интеграции

Операции и SQL-отчёты остаются в PostgreSQL. RAG сейчас используется в помощнике по документам; персональная классификация транзакций, сохранённые уточнения кнопками и обучение на исправлениях остаются этапами roadmap. Google-таблица не изменяется и не импортируется этой интеграцией.

Для одной CPU VM достаточно обслуживать backend, frontend и PostgreSQL: модели и поисковый индекс находятся у OpenAI. Перед публичным развёртыванием по-прежнему требуется аутентификация. Оригиналы документов храните отдельно: текущая версия не ведёт локальный архив материалов. Индексация асинхронная; удаление из OpenAI может отражаться в поиске с задержкой. Управление удалением документов из UI пока не добавлено.

Использование облачного хранилища и модели тарифицируется OpenAI; проверяйте размер индекса и использование API в проекте.

## Проверки

`cd backend && mvn test` запускает JUnit-тесты с подменённым HTTP API: загрузка/статусы, owner-фильтры, reranking, ошибки и очистка после неудачного прикрепления. Они не требуют OpenAI key или PostgreSQL и не создают платных запросов. `cd frontend && npm run build` проверяет сборку интерфейса.

Контракты: [Retrieval](https://developers.openai.com/api/docs/guides/retrieval), [Vector Store search](https://developers.openai.com/api/reference/resources/vector_stores/methods/search), [Files upload](https://developers.openai.com/api/reference/resources/files/methods/create), [цены](https://developers.openai.com/api/docs/pricing).

Импорт уже известных категорий из истории Google Sheets/CSV: [docs/knowledge-import.md](../docs/knowledge-import.md). Подключение PostgreSQL на ВМ: [docs/database-setup.md](../docs/database-setup.md).

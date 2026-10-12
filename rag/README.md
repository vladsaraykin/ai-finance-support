# Локальный RAG-сервис

Compose поднимает [Infinity](https://github.com/michaelfeil/infinity) с двумя моделями и [Qdrant](https://qdrant.tech/documentation/): `BAAI/bge-m3` создаёт мультиязычные embeddings, `Alibaba-NLP/gte-multilingual-reranker-base` пересортировывает найденные фрагменты. Spring backend сам нарезает и индексирует текстовые документы: embedding → Qdrant; при вопросе — embedding → Qdrant search → reranking → OpenAI ответ с найденными источниками.

Модели выбраны для русского и других языков. BGE-M3 выдаёт 1024-мерные векторы; размер коллекции в backend по умолчанию тоже 1024. Оба сервиса используют один GPU, batch ограничен двумя элементами. На GTX 1060 с 6 GB VRAM это компактный локальный старт, но скорость зависит от длины документов и параллельных запросов.

## Windows 10: запуск

Нужны Docker Desktop с WSL 2 backend, актуальные обновления Windows 10, WSL kernel (`wsl --update`) и NVIDIA Windows driver с поддержкой CUDA в WSL. Docker Desktop передаёт NVIDIA GPU Linux-контейнерам через WSL 2; проверьте, что `--gpus all` доступен в Docker до запуска моделей. Если видеокарта не видна контейнеру, сначала исправьте GPU passthrough — не убирайте `device: cuda` и не считайте, что модель работает на GPU.

В PowerShell:

```powershell
cd rag
Copy-Item .env.example .env
notepad .env
docker compose -f docker-compose.yml up -d
docker compose -f docker-compose.yml logs -f inference
```

Замените в `rag/.env` оба placeholder на отдельные длинные случайные ключи. Первое включение скачивает модели из Hugging Face; кэш остаётся в named volume. Проверьте сервисы:

```powershell
curl.exe http://localhost:7997/health
curl.exe -H "api-key: ВАШ_QDRANT_API_KEY" http://localhost:6333/collections
```

После старта сервисы доступны backend-машине как `http://192.168.1.34:7997` (Infinity) и `http://192.168.1.34:6333` (Qdrant). Ограничьте Windows Firewall входящие порты `7997` и `6333` адресом backend-машины, не открывайте их всему интернету.

## Настройки backend

Установите `RAG_ENABLED=true`, URL и API-ключи обоих сервисов из `.env`. `RAG_VECTOR_SIZE` должен соответствовать embedding-модели. Первая загрузка документа автоматически создаёт коллекцию `finance_knowledge`. Доступ к документам фильтруется по текущему `FINANCE_OWNER_ID`.

Документы пока добавляются как текст через `POST /api/knowledge/documents`; PDF/Office-парсинг и массовый импорт не включены. Типы: `RULE`, `FINANCIAL_GUIDE`, `STORE_CATALOG`, `PRODUCT_CATALOG`, `OTHER`. В Qdrant сохраняются текстовые фрагменты и их метаданные. RAG используется для правил, финансовых документов и соответствий магазин/товар, но не для достоверных итогов и записей транзакций — эти данные берутся из PostgreSQL.

Проверка embedding API из PowerShell:

```powershell
curl.exe -X POST http://localhost:7997/embeddings `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer ВАШ_INFINITY_API_KEY" `
  -d '{"model":"BAAI/bge-m3","input":["query: где продаётся овсяное молоко?"]}'
```

Infinity предоставляет OpenAI-style embeddings и отдельный rerank endpoint. RAG API поиска не стандартизирован этим протоколом: его реализует Spring backend поверх Infinity и Qdrant.

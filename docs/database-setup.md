# Подключение удалённой PostgreSQL

Backend подключается к `45.151.31.104:5432`; Flyway создаёт таблицы при первом старте приложения. Подготовьте отдельные базу `finance_support` и роль приложения `finance_app`. Не используйте суперпользователя PostgreSQL в `DATABASE_USERNAME`.

## Создание роли и базы

Из корня проекта запустите:

```bash
psql -h 45.151.31.104 -p 5432 -U postgres -d postgres -W -v ON_ERROR_STOP=1 -f docs/database/remote-postgres-bootstrap.psql
```

Команда запросит пароль администратора, затем скрипт — новый пароль `finance_app`. Они вводятся интерактивно и не записываются в файл. Замените `postgres` после `-U`, если у вас другое имя администратора. Запустите скрипт от администратора, которому разрешено создавать роли и базы. Команды не идемпотентны: при повторном запуске уже существующей роли или базы PostgreSQL вернёт ошибку.

## Переменные backend

Перед запуском backend задайте переменные окружения, используя пароль роли `finance_app`:

```bash
export DATABASE_URL='jdbc:postgresql://45.151.31.104:5432/finance_support'
export DATABASE_USERNAME='finance_app'
export DATABASE_PASSWORD='пароль-который-ввели-в-prompt'
export AI_ENABLED='true'
export OPENAI_API_KEY='ключ-из-OpenAI-Platform'
export OPENAI_MODEL='gpt-5.6-sol'
export RAG_ENABLED='true'
export RAG_INFERENCE_URL='http://192.168.1.34:7997'
export RAG_INFERENCE_API_KEY='тот-же-INFINITY_API_KEY-что-в-rag/.env'
export RAG_QDRANT_URL='http://192.168.1.34:6333'
export RAG_QDRANT_API_KEY='тот-же-QDRANT_API_KEY-что-в-rag/.env'
```

Не добавляйте реальные пароли или API-ключи в Git, issue или сообщения. Удалённые сервисы должны разрешать сетевой доступ с машины backend; ограничьте PostgreSQL и RAG firewall только доверенными адресами. Для OpenAI требуется, чтобы выбранная модель была доступна вашей API-организации. Сборка и работа с документами не отправляют исходные документы в OpenAI: в OpenAI уходят только вопрос и найденные выдержки, необходимые для ответа.

Для RAG-стека на Windows используйте [docker-compose.yml](../../rag/docker-compose.yml) и инструкции в [`rag/README.md`](../../rag/README.md).

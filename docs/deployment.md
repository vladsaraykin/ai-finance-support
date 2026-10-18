# Размещение на cave

Первый запуск — по IP через HTTP на порту **80**, без домена. Схема: браузер → Caddy (React и `/api`) → Spring Boot на `127.0.0.1:8080` → PostgreSQL на `127.0.0.1:5432`. OpenAI вызывается исходящими HTTPS-запросами. Backend и БД не нужно открывать наружу.

При HTTP логин, пароль и данные передаются без шифрования. Используйте этот режим временно; ниже описано переключение на HTTPS.

## 1. Собрать релиз на ноутбуке

В корне проекта нужны Java 21, Maven и Node, совместимый с установленным Vite (например, Node 22.12+ или 24):

```bash
cd /Users/vsaraikin/IdeaProjects/ai-finance-support
./scripts/package-release.sh
scp dist/finance-support-release.tar.gz cave:finance-support-release.tar.gz
ssh -t cave
```

Сборка запускает backend-тесты, frontend-тесты и production-сборку. Архив содержит JAR, собранный UI, шаблоны и установочные скрипты. `.env`, личные CSV и SQL, пароли и `node_modules` не включаются. Docker на ВМ для этого варианта не требуется.

## 2. Установить на ВМ

Следующие команды выполняются уже **в SSH-сессии cave**:

```bash
mkdir -p ~/finance-release
tar -xzf ~/finance-support-release.tar.gz -C ~/finance-release
sudo bash ~/finance-release/scripts/install-on-vm.sh
```

Установщик ставит Java 21, Caddy и PostgreSQL client, создаёт системного пользователя `finance-support`, каталоги `/opt/finance-support` и `/etc/finance-support`. Готовые реквизиты БД из `/root/.config/finance-support/database.env` подставляются автоматически. В вашем случае PostgreSQL уже настроен.

Если установщик остановился с просьбой указать DB-реквизиты, заполните их в `/etc/finance-support/backend.env` и повторите команду установки. Если PostgreSQL ещё не установлен, сначала запустите `sudo bash ~/finance-release/scripts/setup-vm-postgres.sh`.

Перед заменой JAR backend останавливается и создаётся `pg_dump -Fc` в `/var/backups/finance-support` с доступом только root. Ошибка backup останавливает установку. После установки сервисы нужно запустить командами ниже.

**Если на ВМ уже есть другие сайты**, сначала согласуйте их конфигурацию: установщик заменяет `/etc/caddy/Caddyfile`, сохраняя предыдущую копию в каталоге backups. Занятый другим сервером порт 80 также нужно освободить или настроить существующий proxy.

## 3. Настроить HTTP и подключение к БД

```bash
sudoedit /etc/finance-support/caddy.env
```

Установите:

```dotenv
APP_DOMAIN=:80
```

Затем:

```bash
sudoedit /etc/finance-support/backend.env
```

Проверьте настройки (пароль замените настоящим, остальные значения — по вашей БД):

```dotenv
DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/finance_support
DATABASE_USERNAME=finance_app
DATABASE_PASSWORD=your_actual_database_password
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SESSION_COOKIE_SECURE=false
FINANCE_OWNER_ID=11111111-1111-1111-1111-111111111111
FINANCE_TIMEZONE=Europe/Moscow
```

`SESSION_COOKIE_SECURE=false` обязателен для входа по HTTP. Сохраните UUID прежнего `FINANCE_OWNER_ID`: первый аккаунт должен получить его для доступа к импортированным операциям и документам RAG. Это пароль БД; пароль входа в приложение создаётся отдельно на следующем шаге.

Если параметры БД встречаются в файле несколько раз, оставьте по одному правильному значению каждого параметра. Автоподстановка установщика может добавить их после значений шаблона. Повторная установка существующие env-файлы сохраняет, поэтому проверяйте HTTP-настройки и при обновлении старой установки.

Для OpenAI добавьте собственные значения:

```dotenv
AI_ENABLED=true
OPENAI_API_KEY='your_openai_api_key'
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.6-sol
RAG_ENABLED=true
OPENAI_VECTOR_STORE_ID=vs_your_existing_store
```

У модели должен быть доступ в вашей API-организации; при необходимости измените `OPENAI_MODEL`. Если пока запускаете без AI, оставьте `AI_ENABLED=false` и `RAG_ENABLED=false`. Исходные документы RAG повторно загружать не требуется при сохранении Vector Store и UUID владельца.

Файлы секретов должны оставаться root-only:

```bash
sudo chmod 600 /etc/finance-support/backend.env /etc/finance-support/caddy.env
```

## 4. Создать аккаунт приложения

```bash
sudo bash -c 'set -a; . /etc/finance-support/backend.env; set +a; /opt/finance-support/scripts/create-user.sh'
```

Скрипт применит миграции, создаст логин **vlad13reg** и завершится. Введите согласованный пароль скрыто в приглашении. Введённые символы не отображаются. Повторный запуск сохраняет пароль уже существующего пользователя. Пароль не записывается в командную строку или исходники.

## 5. Запустить backend и UI

Проверьте конфигурацию и включите автозапуск:

```bash
sudo bash -c 'set -a; . /etc/finance-support/caddy.env; set +a; caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile'
sudo systemctl enable --now finance-support
sudo systemctl enable caddy
sudo systemctl restart caddy
sudo systemctl status finance-support caddy --no-pager
```

Caddy раздаёт готовый React UI и перенаправляет `/api` на backend. Отдельный Vite/npm-процесс на ВМ не нужен. Оба сервиса запускаются после перезагрузки ВМ.

Если UFW активен, разрешите HTTP:

```bash
sudo ufw status
sudo ufw allow 80/tcp
```

Также разрешите входящий TCP 80 в firewall провайдера ВМ. Не включайте UFW заново без отдельной проверки доступа по SSH. Порты 5432 и 8080 оставьте закрытыми для внешних подключений.

## 6. Открыть и проверить

В браузере ноутбука или телефона откройте:

```text
http://78.17.37.235/
```

Это IP из вашей текущей настройки `cave`; если адрес ВМ изменился, используйте новый. Войдите как `vlad13reg`, выберите месяц импортированных операций, сверьте отчёт, попробуйте исправить и удалить тестовую запись.

Проверки на ВМ:

```bash
curl -I http://127.0.0.1/
curl -i http://127.0.0.1/api/auth/csrf
curl -i http://127.0.0.1/api/transactions
sudo ss -ltnp
```

Ожидается: UI — `200`; CSRF — `200` с JSON и session cookie; операции без входа — `401`; Caddy слушает порт 80, backend — `127.0.0.1:8080`.

Если не работает:

```bash
sudo journalctl -u finance-support -n 100 --no-pager
sudo journalctl -u caddy -n 100 --no-pager
```

`502` обычно означает, что Caddy не может подключиться к backend: проверьте статус и журнал `finance-support`. Если локальный UI работает, а внешний IP нет — проверьте firewall ВМ и провайдера. Если вход не сохраняется по HTTP — проверьте `SESSION_COOKIE_SECURE=false` и перезапустите backend.

## Обновление приложения

Снова выполните сборку и `scp` на ноутбуке. На ВМ распакуйте архив, запустите установщик, повторите создание пользователя для применения миграций (пароль существующего аккаунта не изменится), затем:

```bash
sudo systemctl restart finance-support caddy
```

Установщик сохраняет env-файлы, делает backup БД и сохраняет предыдущий JAR как `/var/backups/finance-support/previous-finance-support.jar`. Проверьте статус и UI после каждого обновления.

## Переключение на HTTPS позднее

Направьте DNS домена на ВМ. В `/etc/finance-support/caddy.env` замените `APP_DOMAIN=:80` на `APP_DOMAIN=ai-finance.com` (или окончательный домен). В `backend.env` задайте `SESSION_COOKIE_SECURE=true`. Разрешите входящие TCP 80/443, проверьте Caddy той же командой и перезапустите оба сервиса. После этого открывайте `https://ваш-домен/`; Caddy управляет сертификатом. [Переменные и адреса Caddy](https://caddyserver.com/docs/caddyfile/concepts#environment-variables).

## Проверка PostgreSQL и восстановление

Обычные backend-тесты не требуют внешней БД. Для проверки миграций, SQL, изоляции владельцев, изменения месяца, удаления и пересчёта отчётов задайте реквизиты отдельной тестовой БД:

```bash
TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/finance_test \
TEST_DATABASE_USERNAME=finance_test \
TEST_DATABASE_PASSWORD='test-only-password' \
mvn -f backend/pom.xml -Dtest=PostgresPersistenceTest test
```

Тест создаёт и удаляет собственную случайную схему; тестовому пользователю нужно право создания схем. Production-реквизиты для этого теста не используйте.

При неудачном обновлении оставьте backend остановленным и проверьте журнал. Откат JAR после миграции требует проверки совместимости схемы. Восстанавливайте dump сначала в отдельную БД через `pg_restore`; переключайте приложение после сверки восстановленных данных. Копии перед релизом не заменяют регулярное резервирование вне ВМ.

# PostgreSQL на ВМ cave

На ВМ нужна отдельная база `finance_support` и роль `finance_app`. Приложение подключается к PostgreSQL, а документы RAG — к OpenAI Vector Stores. Подключения к базе и OpenAI настраиваются независимо.

## Создать базу и пользователя

Из корня проекта на ноутбуке:

```sh
scp scripts/setup-vm-postgres.sh cave:finance-setup-postgres.sh
ssh -t cave 'bash ~/finance-setup-postgres.sh'
```

Передача файла перед запуском позволяет повторить команду после обрыва SSH. Вариант `ssh cave 'bash -s' < scripts/setup-vm-postgres.sh` также поддерживается: команды установки и проверки изолированы от stdin скрипта.

Скрипт рассчитан на Ubuntu с systemd и SSH-пользователя, которому доступен `sudo -n`. При необходимости сначала выполните `sudo -v` в интерактивной SSH-сессии и запустите скрипт в этой же сессии. Если PostgreSQL не установлен, скрипт устанавливает пакеты Ubuntu; существующие настройки PostgreSQL не переписываются.

Скрипт создаёт роль без прав суперпользователя и отдельную БД, генерирует пароль и сохраняет переменные в `~/.config/finance-support/database.env` на ВМ (права `600`). Пароль в вывод не попадает. Затем выполняется проверка TCP-подключения от имени `finance_app`. Таблицы создаст Flyway при запуске backend.

Повторный запуск использует сохранённые реквизиты и не меняет пароль. Если имя БД/роли уже занято другой конфигурацией, скрипт остановится для проверки. По умолчанию используется PostgreSQL на порту `5432`; другой существующий кластер можно выбрать через `FINANCE_POSTGRES_PORT`. Автоматического переноса данных из старой БД этот скрипт не выполняет.

Если нет `sudo -n`, перенесите скрипт и запустите в интерактивной сессии:

```sh
scp scripts/setup-vm-postgres.sh cave:/tmp/finance-setup-postgres.sh
ssh -t cave 'sudo -v && bash /tmp/finance-setup-postgres.sh'
```

Если SSH оборвался во время `apt-get update`, подключитесь повторно и запустите сохранённый скрипт ещё раз. Обрыв сам по себе не подтверждает нехватку памяти или повреждение пакетов. Если он повторяется, проверьте доступность ВМ, `uptime`, `free -h` и `sudo journalctl -k -b -n 100 --no-pager`. Скрипт выводит этап выполнения; при обычной ошибке команды — этап и код возврата. Сообщение `Database connection verified` означает, что создание БД и проверка входа завершились.

## Подключиться с ноутбука

Сохраните конфигурацию локально в исключённый из Git каталог:

```sh
mkdir -p data/import
chmod 700 data/import
scp cave:.config/finance-support/database.env data/import/vm-database.env
chmod 600 data/import/vm-database.env
```

В отдельном терминале откройте туннель и оставьте его работать:

```sh
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 \
  -L 127.0.0.1:15432:127.0.0.1:5432 cave
```

PostgreSQL не требуется открывать во внешний интернет. При другом порте PostgreSQL замените последний `5432` в туннеле. Если локальный `15432` занят, выберите другой локальный порт и укажите его в `DATABASE_URL`.

В терминале запуска backend из корня проекта загрузите свой `.env` с настройками OpenAI, затем реальные реквизиты БД и адрес туннеля:

```sh
set -a
. ./.env
. ./data/import/vm-database.env
set +a
export DATABASE_URL='jdbc:postgresql://127.0.0.1:15432/finance_support'
cd backend
mvn spring-boot:run
```

Для IDE используйте те же `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` в Run Configuration. Не копируйте placeholder-пароль из `.env.example` вместо сгенерированного пароля.

После миграций создайте аккаунт по [authentication.md](authentication.md) и войдите через UI. Запросы `/api/config` и `/api/transactions` теперь требуют сессию; без входа возвращается `401`. Пустой список операций допустим для новой базы. Если Flyway не может применить миграции, backend сообщит ошибку при старте.

## Когда backend тоже работает на ВМ

Для процесса backend на `cave` загрузите `.env` с OpenAI, затем `~/.config/finance-support/database.env`. Его `DATABASE_URL` уже указывает на `127.0.0.1:5432`, и SSH-туннель не нужен. Секреты должны находиться в окружении backend, а не frontend.

Развёртывание веб-приложения с HTTPS, входом и настраиваемым доменом описано в [deployment.md](deployment.md).

## Прежняя внешняя БД

Ранее использовавшаяся PostgreSQL на `45.151.31.104:5432` автоматически не переносится. Скрипт `docs/database/remote-postgres-bootstrap.psql` остаётся для ручной первичной настройки той БД; он не идемпотентен. При переносе существующей истории сначала сделайте backup и сверку, затем меняйте `DATABASE_URL`.

RAG настраивается по [rag/README.md](../rag/README.md); импорт истории описан в [knowledge-import.md](knowledge-import.md). Справка PostgreSQL: [создание базы](https://www.postgresql.org/docs/current/manage-ag-createdb.html), [файл паролей](https://www.postgresql.org/docs/current/libpq-pgpass.html).

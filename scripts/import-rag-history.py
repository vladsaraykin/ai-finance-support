#!/usr/bin/env python3
"""Prepare category examples from an operations CSV; upload only with --upload."""
import argparse
import csv
import fcntl
import hashlib
import getpass
import http.cookiejar
import io
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse, urlencode
from urllib.request import Request, urlopen, build_opener, HTTPCookieProcessor, HTTPRedirectHandler

AUTH_OPENER = None
AUTH_CSRF = None


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def authenticate(api_url, login):
    global AUTH_OPENER, AUTH_CSRF
    parsed = urlparse(api_url)
    if not parsed.hostname or (parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in ("localhost", "127.0.0.1", "::1"))) or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Для входа используйте HTTPS или HTTP через локальный SSH-туннель.")
    password = getpass.getpass("Пароль приложения: ")
    AUTH_OPENER = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
    try:
        token = request(api_url, "/api/auth/csrf")
        req = Request(api_url + "/api/auth/login", data=urlencode({"username": login, "password": password}).encode(),
                      headers={"Content-Type": "application/x-www-form-urlencoded", token["headerName"]: token["token"]})
        with AUTH_OPENER.open(req, timeout=30) as response:
            json.load(response)
        AUTH_CSRF = request(api_url, "/api/auth/csrf")
    except (HTTPError, URLError, TimeoutError, OSError, KeyError, json.JSONDecodeError):
        raise RuntimeError("Не удалось войти. Проверьте логин, пароль и доступность backend.") from None


REQUIRED = ("Тип", "Категория", "Описание")
TYPES = {"расход": "Расход", "доход": "Доход"}
INTRO = ("Подтверждённые исторические примеры классификации пользователя. Это примеры, а не безусловные правила для магазина. "
         "Даты, суммы и способы оплаты прошлых операций не определяют значения новой операции.\n\n")


def read_examples(path):
    raw = Path(path).read_text(encoding="utf-8-sig")
    rows = None
    header = None
    start = None
    for delimiter in (",", ";", "\t"):
        candidate = list(csv.reader(io.StringIO(raw), delimiter=delimiter))
        for index, row in enumerate(candidate[:10]):
            names = [cell.strip() for cell in row]
            if all(name in names for name in REQUIRED):
                rows, header, start = candidate, names, index + 1
                break
        if rows is not None:
            break
    if rows is None:
        raise ValueError("Не найдены столбцы Тип, Категория, Описание в первых десяти строках CSV.")
    indices = {name: header.index(name) for name in REQUIRED}
    examples = {}
    skipped = []
    transfers = 0
    for number, row in enumerate(rows[start:], start=start + 1):
        if not any(cell.strip() for cell in row):
            continue
        def cell(name):
            index = indices[name]
            return row[index].strip() if index < len(row) else ""
        operation_type = cell("Тип").casefold()
        if operation_type in ("перевод", "transfer"):
            transfers += 1
            continue
        category, description = cell("Категория"), cell("Описание")
        if operation_type not in TYPES or not category or not description:
            skipped.append(number)
            continue
        if len(category) > 160 or len(description) > 2000:
            raise ValueError(f"Строка {number}: слишком длинная категория или описание; проверьте исходный CSV.")
        # Collapse whitespace to keep every example a single independently searchable line.
        category, description = " ".join(category.split()), " ".join(description.split())
        key = (TYPES[operation_type], category, description.casefold())
        examples.setdefault(key, description)
    return examples, skipped, transfers


def documents(examples):
    groups = {}
    for (operation_type, category, _), description in sorted(examples.items()):
        groups.setdefault((operation_type, category), []).append(
            f"Описание: {description}; Тип: {operation_type}; Категория: {category}.\n")
    result = []
    for (operation_type, category), lines in sorted(groups.items()):
        chunks = []
        text = INTRO
        for line in lines:
            if len(text) + len(line) > 40_000:
                chunks.append(text)
                text = INTRO
            text += line
        chunks.append(text)
        for index, text in enumerate(chunks, start=1):
            result.append({"title": f"История: {operation_type} / {category} / {index}",
                           "source": "Снимок журнала операций Google Sheets", "documentType": "RULE", "text": text})
    return result


def private_json(path, data, jsonl=False):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with open(temporary, "w", encoding="utf-8") as stream:
        os.chmod(temporary, 0o600)
        if jsonl:
            for item in data:
                stream.write(json.dumps(item, ensure_ascii=False) + "\n")
        else:
            json.dump(data, stream, ensure_ascii=False, indent=2)
    temporary.replace(path)


def request(base, path, body=None):
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if payload is not None and AUTH_CSRF:
        headers[AUTH_CSRF["headerName"]] = AUTH_CSRF["token"]
    req = Request(base + path, data=payload, headers=headers)
    try:
        with (AUTH_OPENER.open(req, timeout=100) if AUTH_OPENER else urlopen(req, timeout=100)) as response:
            return json.load(response)
    except HTTPError as error:
        # Do not print provider bodies, personal text, or credentials.
        raise RuntimeError(f"Backend вернул HTTP {error.code} для {path}. Проверьте его настройки и журнал.") from None
    except (URLError, TimeoutError, json.JSONDecodeError, OSError):
        raise RuntimeError(f"Не удалось получить ответ backend для {path}.") from None


def upload(docs, api_url, state_path):
    parsed = urlparse(api_url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Укажите HTTP(S) URL backend без пароля, query и fragment.")
    config = request(api_url, "/api/config")
    scope = config.get("ragScope")
    if not config.get("ragEnabled") or not scope:
        raise ValueError("Backend должен работать с RAG_ENABLED=true и поддерживать ragScope в /api/config.")
    path = Path(state_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Prevent two local importers from uploading the same prepared document concurrently.
    with open(path.with_name(path.name + ".lock"), "a") as lock:
        os.chmod(lock.name, 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        state = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        counts = {"uploaded": 0, "completed": 0, "in_progress": 0, "failed": 0}
        for document in docs:
            digest = hashlib.sha256(json.dumps([api_url, scope, document], ensure_ascii=False, sort_keys=True).encode()).hexdigest()
            entry = state.get(digest)
            if entry and not entry.get("fileId"):
                raise RuntimeError("Предыдущая отправка завершилась без подтверждения. Проверьте OpenAI Files и журнал импорта перед повтором; автоматическая отправка остановлена во избежание дубля.")
            if entry:
                result = request(api_url, "/api/knowledge/documents/" + quote(entry["fileId"], safe=""))
            else:
                state[digest] = {"status": "sending", "scope": scope}
                private_json(path, state)
                result = request(api_url, "/api/knowledge/documents", document)
                counts["uploaded"] += 1
            if result.get("status") not in ("in_progress", "completed", "failed", "cancelled") or not result.get("fileId"):
                raise RuntimeError("Backend вернул некорректный статус; проверьте журнал импорта перед повтором.")
            state[digest] = result
            private_json(path, state)
            key = result["status"] if result["status"] in counts else "failed"
            counts[key] += 1
        print("Результат: " + ", ".join(f"{key}={value}" for key, value in counts.items()))
        if counts["failed"]:
            raise RuntimeError("Есть ошибки индексации. fileId сохранены в журнале; проверьте их перед повторной загрузкой.")
        if counts["in_progress"]:
            print("Индексация продолжается. Повторите ту же команду позже: она проверит статусы без повторной отправки.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_file")
    parser.add_argument("--output", default="data/import/rag-history.jsonl")
    parser.add_argument("--upload", action="store_true")
    parser.add_argument("--api-url", default="http://127.0.0.1:8080")
    parser.add_argument("--login", default="vlad13reg", help="Логин приложения; пароль будет запрошен скрыто")
    parser.add_argument("--state", default="data/import/rag-import-state.json")
    parser.add_argument("--allow-skipped", action="store_true", help="Allow explicitly reported incomplete rows")
    args = parser.parse_args()
    examples, skipped, transfers = read_examples(args.csv_file)
    docs = documents(examples)
    if not docs:
        raise ValueError("В CSV не найдено примеров для загрузки.")
    print(f"Уникальных примеров: {len(examples)}; документов: {len(docs)}; переводов исключено: {transfers}.")
    if skipped:
        print("Неполные/неизвестные строки CSV: " + ", ".join(map(str, skipped)))
        if args.upload and not args.allow_skipped:
            raise ValueError("Проверьте указанные строки. Для осознанного пропуска используйте --allow-skipped.")
    private_json(args.output, docs, jsonl=True)
    print(f"Подготовлено локально: {args.output}")
    if args.upload:
        authenticate(args.api_url.rstrip("/"), args.login)
        upload(docs, args.api_url.rstrip("/"), args.state)
    else:
        print("Предварительный просмотр. Загрузка в OpenAI выполняется только с --upload через работающий backend.")


if __name__ == "__main__":
    os.umask(0o077)
    try:
        main()
    except (ValueError, RuntimeError, OSError, csv.Error) as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        sys.exit(1)

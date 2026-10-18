#!/usr/bin/env python3
"""One-time CSV -> PostgreSQL import. Default: validate and prepare SQL, no DB writes."""
import argparse
import csv
from datetime import datetime, time
from decimal import Decimal
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import unquote, urlparse
from uuid import UUID, uuid5, NAMESPACE_URL
from zoneinfo import ZoneInfo

HEADERS = ("Дата", "Тип", "Категория", "Описание", "Сумма, ₽", "Способ оплаты")
TYPES = {"Доход": "INCOME", "Расход": "EXPENSE", "Перевод": "TRANSFER"}
PAYMENTS = {"Карта", "Наличные", "Перевод", "Другое"}
CATEGORIES = {"Продукты": "GROCERIES", "Кафе и рестораны": "RESTAURANTS", "Транспорт": "TRANSPORT",
              "Жильё": "HOUSING", "Здоровье": "HEALTH", "Развлечения": "ENTERTAINMENT",
              "Коммунальные": "UTILITIES", "Коммунальные услуги": "UTILITIES", "Зарплата": "SALARY",
              "Прочее": "OTHER", "Другое": "OTHER"}
DEFAULT_OWNER = "11111111-1111-1111-1111-111111111111"


def money(value):
    value = value.strip()
    value = re.sub(r"^(?:р\.|руб\.?|RUB|₽)\s*", "", value, flags=re.IGNORECASE)
    value = re.sub(r"\s*(?:руб\.?|RUB|₽)$", "", value, flags=re.IGNORECASE)
    value = re.sub(r"[ \u00a0\u202f]", "", value)
    if not re.fullmatch(r"[0-9]+(?:[,.][0-9]{1,2})?", value):
        raise ValueError("некорректная сумма RUB")
    amount = Decimal(value.replace(",", "."))
    if amount <= 0 or amount >= Decimal("100000000000000000"):
        raise ValueError("сумма должна быть положительной и помещаться в NUMERIC(19,2)")
    return format(amount, ".2f")


def parse_csv(path, owner_id, source_id, first_data_row=7, timezone="Europe/Moscow"):
    owner_id = str(UUID(owner_id))
    if not source_id or len(source_id) > 200 or any(ord(c) < 32 for c in source_id):
        raise ValueError("source-id должен содержать 1–200 символов без управляющих символов")
    if first_data_row < 1:
        raise ValueError("first-data-row должен быть положительным")
    zone = ZoneInfo(timezone)
    raw = Path(path).read_text(encoding="utf-8-sig")
    rows, names, start = None, None, None
    for delimiter in (",", ";", "\t"):
        candidate = list(csv.reader(io.StringIO(raw), delimiter=delimiter))
        for index, row in enumerate(candidate[:10]):
            header = [cell.strip() for cell in row]
            if all(name in header for name in HEADERS):
                if len(set(header)) != len(header):
                    # Blank trailing Google columns are harmless; duplicate named columns are ambiguous.
                    nonblank = [name for name in header if name]
                    if len(nonblank) != len(set(nonblank)):
                        raise ValueError("В CSV повторяются названия столбцов")
                rows, names, start = candidate, header, index + 1
                break
        if rows is not None:
            break
    if rows is None:
        raise ValueError("Не найдены столбцы: " + ", ".join(HEADERS))
    positions = {name: names.index(name) for name in names if name}
    result, errors = [], []
    for offset, row in enumerate(rows[start:]):
        if not any(cell.strip() for cell in row):
            continue
        source_row = first_data_row + offset
        def cell(name):
            index = positions.get(name)
            return row[index].strip() if index is not None and index < len(row) else ""
        try:
            if cell("Проверка") not in ("", "OK"):
                raise ValueError("Google Sheets отмечает строку как ошибочную")
            occurred = None
            for fmt in ("%d.%m.%Y", "%Y-%m-%d"):
                try:
                    occurred = datetime.combine(datetime.strptime(cell("Дата"), fmt).date(), time(12), tzinfo=zone)
                    break
                except ValueError:
                    pass
            if occurred is None:
                raise ValueError("ожидается дата ДД.ММ.ГГГГ или ГГГГ-ММ-ДД")
            if cell("Тип") not in TYPES:
                raise ValueError("неизвестный тип операции")
            category, description, payment = cell("Категория"), cell("Описание"), cell("Способ оплаты")
            if not category or len(category) > 160 or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in category):
                raise ValueError("некорректная категория")
            if not description or len(description) > 500 or "\x00" in description:
                raise ValueError("описание должно содержать 1–500 символов")
            if payment not in PAYMENTS:
                raise ValueError("ожидается явный способ оплаты: Карта, Наличные, Перевод или Другое")
            amount = money(cell("Сумма, ₽"))
            fields = {"date": occurred.date().isoformat(), "type": TYPES[cell("Тип")], "amount": amount,
                      "category": CATEGORIES.get(category, category), "originalCategory": category,
                      "description": description, "paymentMethod": payment, "currency": "RUB"}
            fingerprint = hashlib.sha256(json.dumps(fields, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
            identity = f"{owner_id}:{source_id}:{source_row}"
            result.append({**fields, "id": str(uuid5(NAMESPACE_URL, identity)), "ownerId": owner_id,
                           "occurredAt": occurred.isoformat(), "source": source_id, "sourceRow": source_row,
                           "requestKey": "import:" + hashlib.sha256(identity.encode()).hexdigest(), "fingerprint": fingerprint})
        except ValueError as error:
            errors.append(f"Строка таблицы {source_row}: {error}")
    if errors:
        raise ValueError("Проверка не пройдена; импорт остановлен:\n" + "\n".join(errors))
    if not result:
        raise ValueError("В CSV нет операций")
    return result


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def sql_for(rows):
    values = []
    for row in rows:
        values.append("(" + ",".join(literal(row[key]) for key in (
            "id", "ownerId", "requestKey", "type", "amount", "currency", "occurredAt", "category",
            "description", "paymentMethod", "source", "sourceRow", "originalCategory", "fingerprint")) + ")")
    columns = "id,owner_id,request_key,transaction_type,amount,currency,occurred_at,category,description,payment_method,import_source,import_row,import_category,import_fingerprint"
    return """BEGIN;
SET LOCAL standard_conforming_strings = on;
SET LOCAL lock_timeout = '15s';
SET LOCAL statement_timeout = '90s';
CREATE TEMP TABLE finance_import_stage (LIKE finance_transactions INCLUDING DEFAULTS) ON COMMIT DROP;
INSERT INTO finance_import_stage (""" + columns + ") VALUES\n" + ",\n".join(values) + ";\n" + """
DO $import_guard$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text || ':' || import_source, 0))
        FROM finance_import_stage LIMIT 1;
    IF EXISTS (
        SELECT FROM finance_import_stage s JOIN finance_transactions t
            ON t.owner_id = s.owner_id AND t.request_key = s.request_key
        WHERE t.import_fingerprint IS DISTINCT FROM s.import_fingerprint
           OR t.import_source IS DISTINCT FROM s.import_source OR t.import_row IS DISTINCT FROM s.import_row
    ) THEN
        RAISE EXCEPTION 'Previously imported rows changed. Import stopped; reconcile the source snapshot before retrying.';
    END IF;
END;
$import_guard$;
WITH inserted AS (
    INSERT INTO finance_transactions (""" + columns + ")\n    SELECT " + columns + " FROM finance_import_stage\n" + """
    ON CONFLICT (owner_id, request_key) DO NOTHING
    RETURNING id
)
SELECT json_build_object('inserted', count(*), 'skipped', (SELECT count(*) FROM finance_import_stage) - count(*)) FROM inserted;
COMMIT;
"""


def write_private(path, text):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as stream:
        os.chmod(path, 0o600)
        stream.write(text)


def apply_sql(sql):
    if not shutil.which("psql"):
        raise ValueError("Нужен psql (на macOS: brew install libpq и добавление его bin в PATH)")
    raw_url = os.environ.get("DATABASE_URL", "").removeprefix("jdbc:")
    target = urlparse(raw_url)
    username, password = os.environ.get("DATABASE_USERNAME", ""), os.environ.get("DATABASE_PASSWORD", "")
    if target.scheme not in ("postgresql", "postgres") or not target.hostname or not target.path.strip("/") or target.username or target.password or target.query or target.fragment:
        raise ValueError("Задайте DATABASE_URL=jdbc:postgresql://host:port/database без встроенных паролей и дополнительных параметров")
    if not username or not password or "replace_with" in password:
        raise ValueError("Загрузите реальные DATABASE_USERNAME и DATABASE_PASSWORD из vm-database.env")
    host, port, database = target.hostname, target.port or 5432, unquote(target.path[1:])
    fields = [host, str(port), database, username, password]
    if any("\n" in field or "\r" in field or "\x00" in field for field in fields):
        raise ValueError("Некорректные реквизиты PostgreSQL")
    with tempfile.TemporaryDirectory(prefix="finance-import-") as directory:
        passfile = Path(directory) / "pgpass"
        write_private(passfile, ":".join(value.replace("\\", "\\\\").replace(":", "\\:") for value in fields) + "\n")
        env = {**os.environ, "PGPASSFILE": str(passfile), "PGCLIENTENCODING": "UTF8"}
        env.pop("PGPASSWORD", None)
        completed = subprocess.run(["psql", "-X", "-q", "-w", "-v", "ON_ERROR_STOP=1", "-h", host,
                                    "-p", str(port), "-U", username, "-d", database], input=sql, text=True, env=env)
        if completed.returncode:
            raise RuntimeError("Импорт не подтверждён; проверьте ошибку psql. При SQL-ошибке изменения откатываются. При обрыве связи во время COMMIT повторите тот же снимок с тем же source-id.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_file")
    parser.add_argument("--source-id", required=True, help="Stable identifier of this source sheet; keep unchanged on retries")
    parser.add_argument("--first-data-row", type=int, default=7, help="Original sheet row of the first data line (default 7)")
    parser.add_argument("--owner-id", default=os.environ.get("FINANCE_OWNER_ID", DEFAULT_OWNER))
    parser.add_argument("--timezone", default=os.environ.get("FINANCE_TIMEZONE", "Europe/Moscow"))
    parser.add_argument("--output", default="data/import/transactions-import.sql")
    parser.add_argument("--apply", action="store_true", help="Write all validated rows in one database transaction")
    args = parser.parse_args()
    rows = parse_csv(args.csv_file, args.owner_id, args.source_id, args.first_data_row, args.timezone)
    sql = sql_for(rows)
    write_private(args.output, sql)
    print(f"Проверено операций: {len(rows)}; владелец: {rows[0]['ownerId']}; источник: {args.source_id}")
    totals = {}
    for row in rows:
        month = row["date"][:7]
        group = totals.setdefault(month, {"INCOME": Decimal(0), "EXPENSE": Decimal(0), "TRANSFER": Decimal(0)})
        group[row["type"]] += Decimal(row["amount"])
    for month, group in sorted(totals.items()):
        print(f"{month}: доход {group['INCOME']:.2f}; расход {group['EXPENSE']:.2f}; разница {group['INCOME']-group['EXPENSE']:.2f}; переводы {group['TRANSFER']:.2f} RUB")
    print(f"SQL для проверки: {args.output}")
    if args.apply:
        apply_sql(sql)
        print("Импорт завершён. Обновите UI и проверьте месячные итоги.")
    else:
        print("Предварительный просмотр. Для записи используйте --apply после применения миграций backend.")


if __name__ == "__main__":
    os.umask(0o077)
    try:
        main()
    except (ValueError, RuntimeError, OSError, csv.Error, KeyError) as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        sys.exit(1)

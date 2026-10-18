import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("import_once", Path(__file__).parents[1] / "import-transactions-once.py")
importer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(importer)
OWNER = "11111111-1111-1111-1111-111111111111"
HEADER = 'Дата,Тип,Категория,Описание,"Сумма, ₽",Способ оплаты,Проверка\n'


class ImportTransactionsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.csv = Path(self.temp.name) / "operations.csv"

    def parse(self, rows, source="sheet-operations", owner=OWNER):
        self.csv.write_text(HEADER + rows, encoding="utf-8")
        return importer.parse_csv(self.csv, owner, source)

    def test_exact_russian_money_format_and_precision(self):
        self.assertEqual("1234.50", importer.money("р.1\u00a0234,50"))
        self.assertEqual("999.99", importer.money("999,99 ₽"))
        self.assertEqual("0.01", importer.money("0,01"))
        self.assertEqual("99999999999999999.99", importer.money("99999999999999999,99"))
        for value in ("0", "-1", "NaN", "1,001", "1.234,50", "$100", "100000000000000000"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                importer.money(value)

    def test_dates_payment_and_custom_category_are_preserved(self):
        row = self.parse('01.10.2026,Расход,Теннис,Аренда корта,"1 500,50",Наличные,OK\n')[0]
        self.assertEqual("2026-10-01T12:00:00+03:00", row["occurredAt"])
        self.assertEqual("1500.50", row["amount"])
        self.assertEqual("Теннис", row["category"])
        self.assertEqual("Наличные", row["paymentMethod"])
        self.assertEqual(7, row["sourceRow"])

    def test_predefined_category_mapping_preserves_original_name(self):
        row = self.parse('01.10.2026,Расход,Продукты,Покупка,100,Карта,OK\n')[0]
        self.assertEqual("GROCERIES", row["category"])
        self.assertEqual("Продукты", row["originalCategory"])

    def test_payment_by_transfer_is_an_expense_and_own_transfer_is_separate_type(self):
        rows = self.parse('01.10.2026,Расход,Теннис,Корт,100,Перевод,OK\n'
                          '01.10.2026,Перевод,Счета,На свой счёт,100,Перевод,OK\n')
        self.assertEqual("EXPENSE", rows[0]["type"])
        self.assertEqual("TRANSFER", rows[1]["type"])

    def test_identical_purchases_on_different_rows_are_not_deduplicated(self):
        purchase = '01.10.2026,Расход,Продукты,Покупка,100,Карта,OK\n'
        rows = self.parse(purchase + purchase)
        self.assertEqual(2, len(rows))
        self.assertEqual(rows[0]["fingerprint"], rows[1]["fingerprint"])
        self.assertNotEqual(rows[0]["requestKey"], rows[1]["requestKey"])
        self.assertNotEqual(rows[0]["id"], rows[1]["id"])

    def test_retry_keeps_identity_but_edit_changes_fingerprint(self):
        original = '01.10.2026,Расход,Продукты,Покупка,100,Карта,OK\n'
        first = self.parse(original)[0]
        self.assertEqual(first, self.parse(original)[0])
        edited = self.parse(original.replace(',100,', ',101,'))[0]
        self.assertEqual(first["requestKey"], edited["requestKey"])
        self.assertNotEqual(first["fingerprint"], edited["fingerprint"])
        self.assertNotEqual(first["requestKey"], self.parse(original, source="other")[0]["requestKey"])
        self.assertNotEqual(first["requestKey"], self.parse(original, owner="22222222-2222-2222-2222-222222222222")[0]["requestKey"])

    def test_incomplete_or_invalid_rows_block_the_whole_import(self):
        for invalid in ('31.02.2026,Расход,Продукты,Покупка,100,Карта,OK\n',
                        '01.10.2026,Расход,Продукты,Покупка,100,,OK\n',
                        '01.10.2026,Расход,Продукты,Покупка,100,Карта,Ошибка\n'):
            with self.subTest(row=invalid), self.assertRaisesRegex(ValueError, "Строка таблицы 8"):
                self.parse('01.10.2026,Доход,Зарплата,Работа,500,Карта,OK\n' + invalid)

    def test_sql_escapes_quotes_and_contains_atomic_conflict_checks(self):
        row = self.parse('01.10.2026,Расход,Теннис,"O\'Brien; $import_guard$",100,Карта,OK\n')[0]
        sql = importer.sql_for([row])
        self.assertIn("O''Brien; $import_guard$", sql)
        self.assertTrue(sql.startswith("BEGIN;"))
        self.assertTrue(sql.endswith("COMMIT;\n"))
        self.assertIn("IS DISTINCT FROM s.import_fingerprint", sql)
        self.assertIn("pg_advisory_xact_lock", sql)
        self.assertIn("ON CONFLICT (owner_id, request_key) DO NOTHING", sql)

    def test_password_is_passed_via_private_file_and_removed_afterwards(self):
        environment = {"DATABASE_URL": "jdbc:postgresql://127.0.0.1:15432/finance_support",
                       "DATABASE_USERNAME": "finance_app", "DATABASE_PASSWORD": "secret:with\\special", "PGPASSWORD": "old"}
        seen_path = None
        def psql(args, **kwargs):
            nonlocal seen_path
            seen_path = Path(kwargs["env"]["PGPASSFILE"])
            self.assertEqual(0o600, seen_path.stat().st_mode & 0o777)
            self.assertIn('secret\\:with\\\\special', seen_path.read_text())
            self.assertNotIn(environment["DATABASE_PASSWORD"], " ".join(args))
            self.assertNotIn("PGPASSWORD", kwargs["env"])
            return subprocess.CompletedProcess(args, 0)
        with patch.dict(os.environ, environment), patch.object(importer.shutil, "which", return_value="psql"), patch.object(importer.subprocess, "run", side_effect=psql):
            importer.apply_sql("BEGIN; COMMIT;")
        self.assertFalse(seen_path.exists())

    def test_failed_psql_is_not_reported_as_success(self):
        environment = {"DATABASE_URL": "jdbc:postgresql://127.0.0.1:15432/finance_support",
                       "DATABASE_USERNAME": "finance_app", "DATABASE_PASSWORD": "secret"}
        with patch.dict(os.environ, environment), patch.object(importer.shutil, "which", return_value="psql"), patch.object(importer.subprocess, "run", return_value=subprocess.CompletedProcess([], 3)):
            with self.assertRaises(RuntimeError):
                importer.apply_sql("BEGIN; COMMIT;")


if __name__ == "__main__":
    unittest.main()

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("import_history", Path(__file__).parents[1] / "import-rag-history.py")
importer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(importer)


class ImportHistoryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def read(self, text):
        path = self.root / "operations.csv"
        path.write_text(text, encoding="utf-8")
        return importer.read_examples(path)

    def test_google_headers_bom_duplicates_and_personal_categories(self):
        examples, skipped, transfers = self.read(
            '\ufeffЖурнал операций\n\nДата,Тип,Категория,Описание,Сумма,Способ оплаты\n'
            '01.10.2026,Расход,Теннис,Корт,1500,Карта\n'
            '02.10.2026,Расход,Теннис,корт,1800,Наличные\n')
        self.assertEqual(1, len(examples))
        self.assertEqual([], skipped)
        text = importer.documents(examples)[0]["text"]
        self.assertIn("Категория: Теннис", text)
        for private_fact in ("01.10.2026", "02.10.2026", "1500", "1800", "Наличные"):
            self.assertNotIn(private_fact, text)

    def test_semicolon_csv_incomplete_rows_and_transfers(self):
        examples, skipped, transfers = self.read(
            'Тип;Категория;Описание\nРасход;Теннис;Корт\n'
            'Перевод;;На свой счёт\nРасход;;Покупка\n')
        self.assertEqual(1, len(examples))
        self.assertEqual([4], skipped)
        self.assertEqual(1, transfers)

    def test_conflicting_labels_and_income_are_preserved_as_examples(self):
        examples, _, _ = self.read('Тип,Категория,Описание\nРасход,Продукты,Магазин\n'
                                   'Расход,Подарки,Магазин\nДоход,Подработка,Работа\n')
        self.assertEqual(3, len(importer.documents(examples)))

    def test_missing_headers_fail(self):
        with self.assertRaises(ValueError):
            self.read('Description,Amount\nCoffee,200\n')

    def test_large_groups_are_bounded_and_each_example_has_its_category(self):
        examples = {("Расход", "Категория", str(i)): "Описание " + str(i) + "x" * 1500 for i in range(100)}
        docs = importer.documents(examples)
        self.assertGreater(len(docs), 1)
        self.assertTrue(all(len(doc["text"]) <= 40_000 for doc in docs))
        self.assertTrue(all("Категория: Категория" in doc["text"] for doc in docs))

    def test_prepared_files_are_private(self):
        path = self.root / "prepared.jsonl"
        importer.private_json(path, [{"text": "example"}], jsonl=True)
        self.assertEqual(0o600, path.stat().st_mode & 0o777)
        self.assertEqual({"text": "example"}, json.loads(path.read_text()))

    def test_repeat_import_checks_status_without_upload_and_scope_changes_are_isolated(self):
        doc = {"title": "Example", "text": "Content"}
        state = self.root / "state.json"
        status = {"fileId": "file-test", "status": "in_progress"}
        with patch.object(importer, "request", side_effect=[{"ragEnabled": True, "ragScope": "owner-store-A"}, status]) as api:
            importer.upload([doc], "http://localhost:8080", state)
            self.assertEqual("/api/knowledge/documents", api.call_args_list[-1].args[1])
        with patch.object(importer, "request", side_effect=[{"ragEnabled": True, "ragScope": "owner-store-A"}, {**status, "status": "completed"}]) as api:
            importer.upload([doc], "http://localhost:8080", state)
            self.assertEqual("/api/knowledge/documents/file-test", api.call_args_list[-1].args[1])
            self.assertEqual(2, api.call_count)
        with patch.object(importer, "request", side_effect=[{"ragEnabled": True, "ragScope": "owner-store-B"}, status]) as api:
            importer.upload([doc], "http://localhost:8080", state)
            self.assertEqual("/api/knowledge/documents", api.call_args_list[-1].args[1])

    def test_uncertain_upload_is_not_automatically_retried(self):
        doc = {"text": "example"}
        state = self.root / "state.json"
        config = {"ragEnabled": True, "ragScope": "owner-store"}
        with patch.object(importer, "request", side_effect=[config, RuntimeError("connection lost")]):
            with self.assertRaises(RuntimeError):
                importer.upload([doc], "http://localhost:8080", state)
        with patch.object(importer, "request", return_value=config) as api:
            with self.assertRaisesRegex(RuntimeError, "без подтверждения"):
                importer.upload([doc], "http://localhost:8080", state)
            self.assertEqual(1, api.call_count)

    def test_disabled_rag_does_not_upload(self):
        with patch.object(importer, "request", return_value={"ragEnabled": False}) as api:
            with self.assertRaises(ValueError):
                importer.upload([{"text": "example"}], "http://localhost:8080", self.root / "state.json")
            self.assertEqual(1, api.call_count)


if __name__ == "__main__":
    unittest.main()

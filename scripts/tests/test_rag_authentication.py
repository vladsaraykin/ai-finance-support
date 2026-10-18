import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import Mock, patch
from urllib.parse import parse_qs

SPEC = importlib.util.spec_from_file_location("rag_auth_importer", Path(__file__).parents[1] / "import-rag-history.py")
importer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(importer)


class RagAuthenticationTest(unittest.TestCase):
    def test_refuses_password_over_remote_plain_http_and_invalid_urls(self):
        for url in ("http://192.0.2.1:8080", "https://", "https://user:password@example.com", "ftp://localhost"):
            with self.subTest(url=url), patch.object(importer.getpass, "getpass") as password:
                with self.assertRaises(ValueError):
                    importer.authenticate(url, "alice")
                password.assert_not_called()

    def test_login_sends_csrf_and_refreshes_it_with_in_memory_cookie_session(self):
        opener = Mock()
        opener.open.return_value.__enter__ = Mock(return_value=io.StringIO('{"id":"owner","login":"alice"}'))
        opener.open.return_value.__exit__ = Mock(return_value=False)
        tokens = [{"headerName": "X-CSRF-TOKEN", "token": "before"}, {"headerName": "X-CSRF-TOKEN", "token": "after"}]
        with patch.object(importer, "AUTH_OPENER", None), patch.object(importer, "AUTH_CSRF", None), \
                patch.object(importer, "build_opener", return_value=opener), \
                patch.object(importer.getpass, "getpass", return_value="test-password-only"), \
                patch.object(importer, "request", side_effect=tokens):
            importer.authenticate("https://finance.example", "alice")
            request = opener.open.call_args.args[0]
            self.assertEqual(["alice"], parse_qs(request.data.decode())["username"])
            self.assertEqual("before", request.get_header("X-csrf-token"))
            self.assertEqual(tokens[1], importer.AUTH_CSRF)
            self.assertIs(opener, importer.AUTH_OPENER)

    def test_upload_attaches_current_csrf_and_uses_authenticated_opener(self):
        opener = Mock()
        opener.open.return_value.__enter__ = Mock(return_value=io.StringIO('{"status":"completed"}'))
        opener.open.return_value.__exit__ = Mock(return_value=False)
        with patch.object(importer, "AUTH_OPENER", opener), \
                patch.object(importer, "AUTH_CSRF", {"headerName": "X-CSRF-TOKEN", "token": "current"}):
            result = importer.request("https://finance.example", "/api/knowledge/documents", {"text": "Example"})
            request = opener.open.call_args.args[0]
            self.assertEqual("current", request.get_header("X-csrf-token"))
            self.assertEqual({"text": "Example"}, json.loads(request.data))
            self.assertEqual("completed", result["status"])

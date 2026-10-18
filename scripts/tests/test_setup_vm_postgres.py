"""Exercise the stdin launch mode without sudo, packages, or a real PostgreSQL server."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).parents[1] / "setup-vm-postgres.sh"

# These stubs deliberately drain stdin: real package hooks may also read from it.
STUB = r'''
import json, os, pathlib, sys
args = sys.argv[1:]
state = pathlib.Path(os.environ["FINANCE_SETUP_TEST_STATE"])
trace = pathlib.Path(os.environ["FINANCE_SETUP_TEST_TRACE"])
name = pathlib.Path(sys.argv[0]).name
if name in ("flock", "pg_isready"):
    sys.exit(0)
if name == "sudo":
    if "psql" in args:
        name = "admin"
    elif "apt-get" in args:
        name = "apt"
    elif "systemctl" in args:
        name = "systemctl"
    else:
        name = "sudo-check"
raw = sys.stdin.read()
with trace.open("a") as stream:
    stream.write(json.dumps({"command": name, "stdin_bytes": len(raw)}) + "\n")
if name == "apt":
    sys.exit(int(os.environ.get("FINANCE_SETUP_TEST_APT_EXIT", "0")))
if name == "admin":
    if "-Atc" in args:
        query = args[args.index("-Atc") + 1]
        if "rolsuper" in query:
            print("0")
        elif "pg_get_userbyid" in query:
            if state.exists(): print("finance_app")
        else:
            print("1" if state.exists() else "0")
    else:
        if "CREATE ROLE" not in raw or "CREATE DATABASE" not in raw:
            sys.exit(31)
        state.write_text("ready")
if name == "psql":
    print("finance_support|finance_app")
'''


class SetupVmPostgresTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        binaries = self.root / "bin"
        binaries.mkdir()
        self.bash = shutil.which("bash")
        for command in ("mkdir", "chmod", "grep", "sed", "od", "tr", "mktemp", "rm"):
            source = shutil.which(command)
            if source is None:
                self.skipTest(f"{command} is required")
            (binaries / command).symlink_to(source)
        for command in ("sudo", "flock", "pg_isready", "psql"):
            path = binaries / command
            path.write_text(f"#!{sys.executable}\n" + STUB)
            path.chmod(0o700)
        # pg_lsclusters is deliberately absent to exercise package installation.
        self.trace = self.root / "trace.jsonl"
        self.env = {**os.environ, "PATH": str(binaries), "XDG_CONFIG_HOME": str(self.root / "config"),
                    "FINANCE_POSTGRES_PORT": "5432", "FINANCE_SETUP_TEST_STATE": str(self.root / "db-state"),
                    "FINANCE_SETUP_TEST_TRACE": str(self.trace)}

    def run_script(self):
        return subprocess.run([self.bash, "-s"], input=SCRIPT.read_text(), env=self.env,
                              capture_output=True, text=True, timeout=15)

    def test_stdin_launch_reaches_verification_and_reuses_credentials(self):
        first = self.run_script()
        self.assertEqual(0, first.returncode, first.stderr)
        self.assertIn("Database connection verified", first.stdout)
        credentials = self.root / "config/finance-support/database.env"
        saved = credentials.read_bytes()
        self.assertEqual(0o600, credentials.stat().st_mode & 0o777)
        password = saved.decode().split("DATABASE_PASSWORD=")[1].strip()
        self.assertNotIn(password, first.stdout + first.stderr)
        second = self.run_script()
        self.assertEqual(0, second.returncode, second.stderr)
        self.assertEqual(saved, credentials.read_bytes())
        calls = [json.loads(line) for line in self.trace.read_text().splitlines()]
        self.assertTrue(any(call["command"] == "apt" for call in calls))
        self.assertTrue(any(call["command"] == "admin" and call["stdin_bytes"] > 0 for call in calls))
        for call in calls:
            if call["command"] != "admin":
                self.assertEqual(0, call["stdin_bytes"], call["command"])

    def test_package_failure_reports_stage_and_stops_before_creating_credentials(self):
        self.env["FINANCE_SETUP_TEST_APT_EXIT"] = "42"
        result = self.run_script()
        self.assertEqual(42, result.returncode)
        self.assertIn("installing PostgreSQL packages (exit 42)", result.stderr)
        self.assertFalse((self.root / "config/finance-support/database.env").exists())


if __name__ == "__main__":
    unittest.main()

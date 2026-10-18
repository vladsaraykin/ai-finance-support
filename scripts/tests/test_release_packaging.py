from pathlib import Path
import os
import shutil
import subprocess
import tarfile
import tempfile
import unittest


class ReleasePackagingTest(unittest.TestCase):
    def test_archive_contains_only_runtime_artifacts_and_safe_templates(self):
        repository = Path(__file__).parents[2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in ('scripts', 'backend/target', 'frontend/dist/assets', 'deploy', 'docs', 'data/import', 'bin'):
                (root / path).mkdir(parents=True)
            shutil.copy(repository / 'scripts/package-release.sh', root / 'scripts/package-release.sh')
            for name in ('create-user.sh', 'setup-vm-postgres.sh', 'install-on-vm.sh'):
                (root / 'scripts' / name).write_text('# script\n')
            for name in ('Caddyfile', 'finance-support.service', 'caddy-finance.conf', 'backend.env.example', 'caddy.env.example'):
                (root / 'deploy' / name).write_text('safe template')
            for name in ('deployment.md', 'authentication.md'):
                (root / 'docs' / name).write_text('guide')
            (root / 'backend/target/finance-support-0.1.0-SNAPSHOT.jar').write_text('compiled')
            (root / 'frontend/dist/index.html').write_text('page')
            (root / 'frontend/dist/assets/app.js').write_text('bundle')
            for path in ('deploy/backend.env', 'deploy/private.env', '.env', 'data/import/private.csv'):
                (root / path).write_text('SECRET_MUST_NOT_SHIP')
            for name in ('mvn', 'npm'):
                command = root / 'bin' / name
                command.write_text('#!/bin/sh\nexit 0\n')
                command.chmod(0o755)
            result = subprocess.run(['bash', str(root / 'scripts/package-release.sh')],
                                    env={**os.environ, 'PATH': str(root / 'bin') + os.pathsep + os.environ['PATH']},
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            with tarfile.open(root / 'dist/finance-support-release.tar.gz') as archive:
                files = {member.name.removeprefix('./'): archive.extractfile(member).read()
                         for member in archive.getmembers() if member.isfile()}
            self.assertIn('backend/finance-support.jar', files)
            self.assertIn('frontend/dist/assets/app.js', files)
            self.assertIn('deploy/backend.env.example', files)
            self.assertIn('docs/deployment.md', files)
            self.assertFalse(any(b'SECRET_MUST_NOT_SHIP' in value for value in files.values()))
            self.assertNotIn('deploy/backend.env', files)

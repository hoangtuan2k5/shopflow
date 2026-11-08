"""Exercise production Flyway locations with a compiled SQL/Java migration bundle."""

from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]


class JavaMigrationPreflightTests(unittest.TestCase):
    def run_command(self, args, **kwargs):
        return subprocess.run(args, capture_output=True, text=True, timeout=180, **kwargs)

    def require_success(self, args, **kwargs):
        result = self.run_command(args, **kwargs)
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        return result.stdout

    def test_preflight_resolves_java_and_still_rejects_modified_sql(self):
        name = 'migration-preflight-' + uuid.uuid4().hex[:12]
        network = name + '-network'
        required = re.findall(r'\$\{([A-Z_0-9]+):\?', (ROOT / 'docker-compose.prod.yml').read_text())
        env = dict(os.environ, **{key: 'test-only' for key in required})
        compose = json.loads(self.require_success(
            ['docker', 'compose', '-f', str(ROOT / 'docker-compose.prod.yml'),
             '--profile', 'validation', 'config', '--format', 'json'], env=env))
        locations = compose['services']['flyway']['environment']['FLYWAY_LOCATIONS']
        dockerfile = (ROOT / 'backend/Dockerfile').read_text()
        cli = re.search(r'FROM (flyway/flyway:\S+) AS migration-validator', dockerfile)[1]
        classes = ROOT / 'backend/target/classes'
        self.assertTrue((classes / 'db/migration/V8__add_account_password_setup.class').exists(),
                        'Run backend Maven verify before this check')
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            bundle = folder / 'migrations.jar'
            self.require_success(['jar', '--create', '--file', str(bundle),
                                  '-C', str(classes), 'db/migration'])
            self.require_success(['docker', 'network', 'create', '--internal', network])
            self.addCleanup(self.run_command, ['docker', 'network', 'rm', network])
            self.require_success(['docker', 'run', '-d', '--name', name,
                                  '--network', network, '--tmpfs', '/var/lib/postgresql/data',
                                  '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'postgres:17-alpine'])
            self.addCleanup(self.run_command, ['docker', 'rm', '-f', name])
            for _ in range(40):
                if self.run_command(['docker', 'exec', name, 'pg_isready', '-U', 'postgres']).returncode == 0:
                    break
                time.sleep(1)
            else:
                self.fail('Temporary PostgreSQL did not become ready')

            common = ['docker', 'run', '--rm', '--network', network,
                      '-e', 'FLYWAY_URL=jdbc:postgresql://' + name + ':5432/postgres',
                      '-e', 'FLYWAY_USER=postgres', '-e', 'FLYWAY_SCHEMAS=shopflow',
                      '-e', 'FLYWAY_DEFAULT_SCHEMA=shopflow']

            def validator(jar, command):
                return common + ['-e', 'FLYWAY_LOCATIONS=' + locations,
                                 '-v', str(jar) + ':/flyway/jars/shopflow-migrations.jar:ro',
                                 cli, command, '-ignoreMigrationPatterns=*:pending']

            self.require_success(validator(bundle, 'migrate'))
            version = self.require_success(['docker', 'exec', name, 'psql', '-U', 'postgres',
                                            '-Atc', "SELECT max(version::int) FROM shopflow.flyway_schema_history WHERE success"])
            self.assertEqual(version.strip(), '9')
            self.require_success(validator(bundle, 'validate'))
            old = self.run_command(common + ['-v', str(ROOT / 'backend/src/main/resources/db/migration')
                                             + ':/flyway/sql:ro', cli, 'validate',
                                             '-ignoreMigrationPatterns=*:pending'])
            self.assertNotEqual(old.returncode, 0)
            self.assertIn('not resolved locally: 8', old.stdout + old.stderr)

            changed = folder / 'changed'
            shutil.copytree(classes / 'db', changed / 'db')
            sql = changed / 'db/migration/V7__customer_identity_and_recovery.sql'
            sql.write_text(sql.read_text() + '\nSELECT 1;\n')
            altered_bundle = folder / 'altered.jar'
            self.require_success(['jar', '--create', '--file', str(altered_bundle),
                                  '-C', str(changed), 'db/migration'])
            bad = self.run_command(validator(altered_bundle, 'validate'))
            self.assertNotEqual(bad.returncode, 0)
            self.assertIn('checksum mismatch', (bad.stdout + bad.stderr).lower())


if __name__ == '__main__':
    unittest.main(verbosity=2)

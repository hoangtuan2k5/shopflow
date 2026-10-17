"""Exercise workflow Compose stdin handling with an isolated temporary container."""

from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / '.github/workflows/ci.yml').read_text()
COMMANDS = [
    line.strip() for line in WORKFLOW.splitlines()
    if line.strip().startswith('docker compose ') and
    re.search(r' (run|exec) ', line)
]


class DeployStdinTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        compose = Path(cls.directory.name) / 'compose.yml'
        compose.write_text(
            'services:\n  probe:\n    image: postgres:17-alpine\n'
            '    network_mode: none\n    entrypoint: ["/bin/sh", "-c"]\n'
            '    command: ["sleep 300"]\n'
        )
        cls.compose = ['docker', 'compose', '-p', 'stdin-' + uuid.uuid4().hex[:12],
                       '-f', str(compose)]
        cls.addClassCleanup(cls.cleanup_containers)
        subprocess.run(cls.compose + ['up', '-d'], check=True, capture_output=True,
                       text=True, timeout=180)

    @classmethod
    def cleanup_containers(cls):
        subprocess.run(cls.compose + ['down', '--remove-orphans'], check=True,
                       capture_output=True, timeout=30)

    def command(self, source, exit_code=0):
        tokens = shlex.split(source)
        action = 'run' if 'run' in tokens else 'exec'
        service = next(i for i, token in enumerate(tokens)
                       if token in ('flyway', 'frontend', 'backend'))
        options = tokens[tokens.index(action):service]
        redirections = [token for token in tokens[service:]
                        if token.startswith(('<', '>'))]
        child = ['probe']
        if action == 'exec':
            child += ['sh', '-c']
        child += [f'sleep 1; exit {exit_code}']
        return shlex.join(self.compose + options + child) + ' ' + ' '.join(redirections)

    def execute(self, commands):
        script = 'set -eu\n' + '\n'.join(commands) + '\necho COMPLETE\n'
        return subprocess.run(['bash', '-s'], input=script, capture_output=True,
                              text=True, timeout=30)

    def test_workflow_contains_validation_and_both_probes(self):
        self.assertEqual(len(COMMANDS), 3)
        self.assertIn('flyway validate', COMMANDS[0])
        self.assertIn('exec -T frontend', COMMANDS[1])
        self.assertIn('exec -T backend', COMMANDS[2])

    def test_success_reaches_every_following_step(self):
        commands = []
        for index, source in enumerate(COMMANDS):
            commands += [self.command(source), f'echo AFTER_{index}']
        result = self.execute(commands)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(),
                         ['AFTER_0', 'AFTER_1', 'AFTER_2', 'COMPLETE'])

    def test_each_failure_stops_the_script(self):
        for source in COMMANDS:
            with self.subTest(command=source):
                result = self.execute([self.command(source, exit_code=7)])
                self.assertEqual(result.returncode, 7, result.stderr)
                self.assertNotIn('COMPLETE', result.stdout)

    def test_removing_each_stdin_fix_reproduces_silent_truncation(self):
        for source in COMMANDS:
            with self.subTest(command=source):
                self.assertIn('</dev/null', source)
                result = self.execute([self.command(source.replace('</dev/null', ''))])
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertNotIn('COMPLETE', result.stdout)


if __name__ == '__main__':
    unittest.main(verbosity=2)

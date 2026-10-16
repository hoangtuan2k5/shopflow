"""Check the production HTTPS probe against a temporary localhost TLS server."""

import http.server
from pathlib import Path
import shlex
import ssl
import subprocess
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / '.github/workflows/ci.yml').read_text()
PROBE = shlex.split(next(
    line.strip().split(' >/dev/null')[0]
    for line in WORKFLOW.splitlines()
    if line.strip().startswith('curl ') and '/healthz' in line
))


class HealthHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Length", "3")
        self.end_headers()
        self.wfile.write(b'ok\n')

    def log_message(self, *args):
        pass


class DeployTlsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        cls.cert = Path(cls.directory.name) / 'cert.pem'
        key = Path(cls.directory.name) / 'key.pem'
        subprocess.run([
            'openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
            '-keyout', str(key), '-out', str(cls.cert), '-days', '1',
            '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost',
        ], check=True, capture_output=True)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cls.cert, key)
        cls.server = http.server.HTTPServer(('127.0.0.1', 0), HealthHandler)
        cls.addClassCleanup(cls.server.server_close)
        cls.server.socket = context.wrap_socket(cls.server.socket, server_side=True)
        thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        thread.start()
        cls.addClassCleanup(thread.join)
        cls.addClassCleanup(cls.server.shutdown)

    def probe(self, hostname, trusted):
        url = f'https://{hostname}:{self.server.server_port}/healthz'
        command = ['curl', '-q', *PROBE[1:-1], '--noproxy', '*']
        if trusted:
            command += ['--cacert', str(self.cert)]
        return subprocess.run(command + [url], capture_output=True, text=True, timeout=15)

    def test_production_probe_keeps_certificate_verification(self):
        self.assertEqual(PROBE[-1], 'https://shopflow.hoangtuan.dev/healthz')
        self.assertIn('--fail', PROBE)
        self.assertNotIn('-k', PROBE)
        self.assertNotIn('--insecure', PROBE)
        self.assertNotIn('--no-check-certificate', WORKFLOW)
        self.assertNotIn('--no-check-certificate', (ROOT / 'docker-compose.prod.yml').read_text())

    def test_trusted_certificate_and_matching_hostname_succeed(self):
        result = self.probe('localhost', trusted=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, 'ok\n')

    def test_untrusted_self_signed_certificate_fails(self):
        result = self.probe('localhost', trusted=False)
        self.assertEqual(result.returncode, 60, result.stderr)

    def test_trusted_certificate_with_wrong_hostname_fails(self):
        result = self.probe('127.0.0.1', trusted=True)
        self.assertEqual(result.returncode, 60, result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)

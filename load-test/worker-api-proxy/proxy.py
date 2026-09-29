import http.client
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


TARGET_HOST = os.getenv("TARGET_HOST", "api")
TARGET_PORT = int(os.getenv("TARGET_PORT", "8080"))
FAIL_COMPLETE = os.getenv("FAIL_COMPLETE", "false").lower() == "true"
CLIENT_READ_TIMEOUT_SECONDS = 10
MAX_REQUEST_BODY_BYTES = 10 * 1024 * 1024
HOP_BY_HOP_HEADERS = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
}


def connection_tokens(headers):
    return {
        token.strip().lower()
        for name, value in headers
        if name.lower() == "connection"
        for token in value.split(",")
        if token.strip()
    }


class WorkerApiProxyHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self._handle()

    def do_POST(self):
        self._handle()

    def _handle(self):
        self.connection.settimeout(CLIENT_READ_TIMEOUT_SECONDS)
        if self.path == "/proxy-health":
            self._send_json(200, {"status": "UP", "failComplete": FAIL_COMPLETE})
            return
        if FAIL_COMPLETE and self.command == "POST" and self.path.endswith("/complete"):
            self._send_json(503, {"message": "synthetic complete callback failure"})
            return

        raw_content_length = self.headers.get("Content-Length", "0")
        try:
            content_length = int(raw_content_length)
        except ValueError:
            self._send_json(400, {"message": "invalid Content-Length"})
            return
        if content_length < 0:
            self._send_json(400, {"message": "negative Content-Length"})
            return
        if content_length > MAX_REQUEST_BODY_BYTES:
            self._send_json(413, {"message": "request body too large"})
            return
        try:
            body = self.rfile.read(content_length) if content_length else None
        except TimeoutError:
            self._send_json(408, {"message": "request body read timed out"})
            return

        request_excluded_headers = HOP_BY_HOP_HEADERS | connection_tokens(self.headers.items())
        headers = {
            name: value
            for name, value in self.headers.items()
            if name.lower() not in request_excluded_headers | {"host", "content-length"}
        }
        connection = http.client.HTTPConnection(TARGET_HOST, TARGET_PORT, timeout=30)
        try:
            connection.request(self.command, self.path, body=body, headers=headers)
            response = connection.getresponse()
            response_body = response.read()
            response_headers = response.getheaders()
            response_excluded_headers = HOP_BY_HOP_HEADERS | connection_tokens(response_headers)
            self.send_response(response.status)
            for name, value in response_headers:
                if name.lower() not in response_excluded_headers | {"content-length"}:
                    self.send_header(name, value)
            self.send_header("Content-Length", str(len(response_body)))
            self.end_headers()
            self.wfile.write(response_body)
        except OSError as error:
            self._send_json(502, {"message": f"upstream unavailable: {type(error).__name__}"})
        finally:
            connection.close()

    def _send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    port = int(os.getenv("PORT", "18081"))
    ThreadingHTTPServer(("0.0.0.0", port), WorkerApiProxyHandler).serve_forever()

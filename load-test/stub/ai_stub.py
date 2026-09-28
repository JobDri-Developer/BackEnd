#!/usr/bin/env python3
"""Synthetic OpenAI/Cohere failure stub. Never forwards traffic upstream."""
import json
import os
import time
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ATTEMPTS = {}
ATTEMPTS_LOCK = threading.Lock()

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length', '0'))
        body = self.rfile.read(length)
        mode = self.headers.get('X-Stub-Mode', os.getenv('STUB_MODE', 'success'))
        delay = float(self.headers.get('X-Stub-Latency-Seconds', os.getenv('STUB_LATENCY_SECONDS', '0')))
        key = self.headers.get('X-Stub-Key', self.path)
        with ATTEMPTS_LOCK:
            attempt = ATTEMPTS.get(key, 0) + 1
            ATTEMPTS[key] = attempt
            retry_before_success = (
                mode == 'retry_then_success'
                and attempt < int(os.getenv('STUB_SUCCESS_ATTEMPT', '3'))
            )
        if delay: time.sleep(delay)
        if retry_before_success:
            return self.reply(429, {'error': {'message': 'synthetic rate limit'}})
        if mode in {'429', '500', '502', '503'}:
            return self.reply(int(mode), {'error': {'message': f'synthetic {mode}'}})
        if mode == 'invalid_json':
            return self.reply_raw(200, b'{invalid-json', 'application/json')
        if self.path.endswith('/v2/embed'):
            return self.cohere(body, mode)
        return self.openai(mode)

    def cohere(self, body, mode):
        try:
            request = json.loads(body or b'{}')
            count = len(request.get('texts', [])) or 1
            dimension = int(request.get('output_dimension', 1024))
        except Exception:
            return self.reply(400, {'error': 'synthetic bad request'})
        if mode == 'dimension_mismatch': dimension = max(1, dimension - 1)
        return self.reply(200, {'embeddings': {'float': [[0.01] * dimension for _ in range(count)]}})

    def openai(self, mode):
        if mode == 'semantic_invalid':
            content = {'jobFit': 999, 'impact': -1, 'completeness': 999, 'feedback': ''}
        else:
            content = {'jobFit': 80, 'impact': 75, 'completeness': 85, 'feedback': 'synthetic load-test response', 'questionAnalyses': [], 'missingKeywords': [], 'keyStrengths': [], 'keyWeaknesses': []}
        return self.reply(200, {
            'id': 'synthetic-response',
            'object': 'response',
            'created_at': int(time.time()),
            'status': 'completed',
            'model': 'synthetic-load-test-model',
            'output': [{
                'id': 'synthetic-message',
                'type': 'message',
                'status': 'completed',
                'role': 'assistant',
                'content': [{
                    'type': 'output_text',
                    'annotations': [],
                    'text': json.dumps(content),
                }],
            }],
        })

    def reply(self, status, payload):
        self.reply_raw(status, json.dumps(payload).encode(), 'application/json')

    def reply_raw(self, status, payload, content_type):
        self.send_response(status); self.send_header('Content-Type', content_type); self.end_headers(); self.wfile.write(payload)

    def log_message(self, fmt, *args):
        print(json.dumps({'message': fmt % args, 'path': self.path}))

if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', int(os.getenv('PORT', '18080'))), Handler).serve_forever()

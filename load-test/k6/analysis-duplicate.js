import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '1m',
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  if (!__ENV.BASE_URL || !__ENV.ACCESS_TOKEN || !__ENV.MOCK_APPLY_ID) {
    throw new Error('BASE_URL, ACCESS_TOKEN, MOCK_APPLY_ID are required; use one synthetic duplicate fixture');
  }
  const response = http.post(
    `${__ENV.BASE_URL}/api/mock-applies/${__ENV.MOCK_APPLY_ID}/analysis`,
    null,
    { headers: { Authorization: `Bearer ${__ENV.ACCESS_TOKEN}`, 'X-Load-Scenario': 'duplicate-request' } },
  );
  check(response, {
    'duplicate is safely reused or already active': (r) => r.status === 200
      && ['PENDING', 'RUNNING', 'SUCCEEDED'].includes(r.json('result.status')),
  });
  sleep(0.1);
}

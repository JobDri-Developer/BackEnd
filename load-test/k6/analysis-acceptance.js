import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const acceptedLatency = new Trend('analysis_acceptance_latency', true);
const publishFailures = new Counter('analysis_publish_failures');
const acceptanceErrors = new Rate('analysis_acceptance_errors');

const targetRps = Number(__ENV.TARGET_RPS || 1);
const duration = __ENV.DURATION || '1m';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(20, targetRps * 2));

export const acceptanceThresholds = {
  analysis_acceptance_errors: ['rate<0.01'],
  analysis_acceptance_latency: ['p(95)<1000', 'p(99)<2000'],
  http_req_failed: ['rate<0.01'],
};

export const options = {
  scenarios: {
    projected_acceptance: {
      executor: 'constant-arrival-rate',
      rate: targetRps,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs: Number(__ENV.MAX_VUS || Math.max(100, targetRps * 5)),
    },
  },
  thresholds: acceptanceThresholds,
};

const ids = (__ENV.MOCK_APPLY_IDS || '').split(',').map((v) => v.trim()).filter(Boolean);

export function runAcceptance(idIndex = Number(__ENV.ID_OFFSET || 0) + exec.scenario.iterationInTest) {
  if (!__ENV.BASE_URL || !__ENV.ACCESS_TOKEN || ids.length === 0) {
    throw new Error('BASE_URL, ACCESS_TOKEN, MOCK_APPLY_IDS are required; use synthetic test accounts only');
  }
  if (idIndex >= ids.length) {
    throw new Error(`MOCK_APPLY_IDS exhausted at index ${idIndex}; new-analysis scenarios require one distinct synthetic ID per iteration`);
  }
  const mockApplyId = ids[idIndex];
  const response = http.post(
    `${__ENV.BASE_URL}/api/mock-applies/${mockApplyId}/analysis`,
    null,
    { headers: { Authorization: `Bearer ${__ENV.ACCESS_TOKEN}`, 'X-Load-Scenario': __ENV.SCENARIO || 'assumption-acceptance' } },
  );
  acceptedLatency.add(response.timings.duration);
  const ok = check(response, {
    'new analysis accepted': (r) => r.status === 200
      && r.json('result.status') === 'PENDING'
      && r.json('result.cached') === false
      && Boolean(r.json('result.taskId')),
  });
  acceptanceErrors.add(!ok);
  if (response.status >= 500 || response.body.includes('PUBLISH_FAILED')) publishFailures.add(1);
}

export default function () {
  runAcceptance();
}

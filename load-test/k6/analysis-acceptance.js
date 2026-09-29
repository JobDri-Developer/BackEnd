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

export const acceptanceSummaryTrendStats = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'];

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
  summaryTrendStats: acceptanceSummaryTrendStats,
  thresholds: acceptanceThresholds,
};

const ids = (__ENV.MOCK_APPLY_IDS || '').split(',').map((v) => v.trim()).filter(Boolean);
const accessTokens = (__ENV.ACCESS_TOKENS || '').split(',').map((v) => v.trim()).filter(Boolean);
const cases = (__ENV.MOCK_APPLY_CASES || '').split(',')
  .map((value) => value.trim())
  .filter(Boolean)
  .map((value) => {
    const parts = value.split(':');
    const mockApplyId = parts[0];
    const tokenIndex = Number(parts[1]);
    if (parts.length !== 2
      || !mockApplyId
      || !/^\d+$/.test(parts[1])
      || !Number.isSafeInteger(tokenIndex)) {
      throw new Error(`invalid MOCK_APPLY_CASES entry: ${value}`);
    }
    return { mockApplyId, tokenIndex };
  });

export function runAcceptance(idIndex = Number(__ENV.ID_OFFSET || 0) + exec.scenario.iterationInTest) {
  const multiUser = cases.length > 0 && accessTokens.length > 0;
  if (!__ENV.BASE_URL || (!multiUser && (!__ENV.ACCESS_TOKEN || ids.length === 0))) {
    throw new Error('BASE_URL and either ACCESS_TOKEN/MOCK_APPLY_IDS or ACCESS_TOKENS/MOCK_APPLY_CASES are required; use synthetic test accounts only');
  }
  const fixtureCount = multiUser ? cases.length : ids.length;
  if (idIndex >= fixtureCount) {
    throw new Error(`synthetic mock applies exhausted at index ${idIndex}; new-analysis scenarios require one distinct synthetic ID per iteration`);
  }
  const loadCase = multiUser ? cases[idIndex] : { mockApplyId: ids[idIndex], tokenIndex: -1 };
  const accessToken = multiUser ? accessTokens[loadCase.tokenIndex] : __ENV.ACCESS_TOKEN;
  if (!accessToken) {
    throw new Error(`missing synthetic access token for fixture index ${idIndex}`);
  }
  const response = http.post(
    `${__ENV.BASE_URL}/api/mock-applies/${loadCase.mockApplyId}/analysis`,
    null,
    { headers: { Authorization: `Bearer ${accessToken}`, 'X-Load-Scenario': __ENV.SCENARIO || 'assumption-acceptance' } },
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

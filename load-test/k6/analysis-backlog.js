import { runAcceptance } from './analysis-acceptance.js';

const backlogCount = Number(__ENV.BACKLOG_COUNT || 300);

if (!Number.isSafeInteger(backlogCount) || backlogCount < 1) {
  throw new Error('BACKLOG_COUNT must be a positive integer');
}

export const options = {
  scenarios: {
    queue_backlog: {
      executor: 'shared-iterations',
      vus: Number(__ENV.BACKLOG_VUS || Math.min(50, backlogCount)),
      iterations: backlogCount,
      maxDuration: __ENV.BACKLOG_MAX_DURATION || '10m',
    },
  },
  thresholds: {
    analysis_acceptance_errors: ['rate==0'],
    http_req_failed: ['rate==0'],
  },
};

export default function () {
  runAcceptance();
}

import exec from 'k6/execution';
import { acceptanceThresholds, runAcceptance } from './analysis-acceptance.js';

// These are planning assumptions, not measured production traffic.
export const options = {
  scenarios: {
    assumption_initial: scenario(24, '100s', '10m', 10),
    assumption_growth: scenario(18, '10s', '10m', 20, '11m'),
    assumption_recruiting_peak: scenario(88, '10s', '10m', 50, '22m'),
    assumption_short_burst: scenario(30, '1s', '2m', 100, '33m'),
  },
  thresholds: acceptanceThresholds,
};

const idOffsets = {
  assumption_initial: 0,
  // constant-arrival-rate can schedule an iteration at the duration boundary.
  // Keep 1% headroom between ID ranges so scenarios never reuse a mock apply.
  assumption_growth: 146,
  assumption_recruiting_peak: 1237,
  assumption_short_burst: 6570,
};

function scenario(rate, timeUnit, duration, preAllocatedVUs, startTime = '0s') {
  return { executor: 'constant-arrival-rate', rate, timeUnit, duration, startTime, preAllocatedVUs, maxVUs: preAllocatedVUs * 4 };
}

export default function () {
  runAcceptance(idOffsets[exec.scenario.name] + exec.scenario.iterationInTest);
}

import exec from 'k6/execution';
import acceptance from './analysis-acceptance.js';

// These are planning assumptions, not measured production traffic.
export const options = {
  scenarios: {
    assumption_initial: scenario(0.24, '10m', 10),
    assumption_growth: scenario(1.8, '10m', 20, '11m'),
    assumption_recruiting_peak: scenario(8.8, '10m', 50, '22m'),
    assumption_short_burst: scenario(30, '2m', 100, '33m'),
  },
};

function scenario(rate, duration, preAllocatedVUs, startTime = '0s') {
  return { executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration, startTime, preAllocatedVUs, maxVUs: preAllocatedVUs * 4 };
}

export default function () {
  if (!__ENV.SCENARIO) __ENV.SCENARIO = exec.scenario.name;
  acceptance();
}

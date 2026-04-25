/**
 * sentinel-rate load test — k6
 *
 * Run: k6 run scripts/load-test.js
 *
 * Simulates 50 virtual users hammering /health for 30 seconds.
 * Validates that the service correctly returns 429s once the window fills
 * and that p99 latency stays under acceptable thresholds.
 */
import http   from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const rateLimited = new Rate('rate_limited');
const blocked     = new Rate('blocked');
const allowed     = new Rate('allowed');
const p99Latency  = new Trend('p99_latency');

export const options = {
  vus:      50,
  duration: '30s',
  thresholds: {
    http_req_duration:    ['p(99)<200'],   // p99 < 200 ms
    http_req_failed:      ['rate<0.01'],   // < 1% non-2xx/4xx errors
    rate_limited:         ['rate>0.3'],    // expect >30% 429s under this load
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/health`);

  p99Latency.add(res.timings.duration);

  check(res, {
    'is 200 or 429 or 403': (r) => [200, 429, 403].includes(r.status),
  });

  if (res.status === 200)  allowed.add(1);
  if (res.status === 429)  rateLimited.add(1);
  if (res.status === 403)  blocked.add(1);

  sleep(0.05); // 50 ms think-time between requests per VU
}

export function handleSummary(data) {
  const p99 = data.metrics['http_req_duration'].values['p(99)'];
  console.log(`\nSummary: p99=${p99.toFixed(1)}ms | 429 rate=${(data.metrics['rate_limited'].values.rate * 100).toFixed(1)}%`);
  return {};
}

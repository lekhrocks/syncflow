import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '2m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 200 },
    { duration: '5m', target: 200 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const responses = http.batch([
    ['GET', `${BASE_URL}/api/dashboard/overview`, null, { tags: { name: 'overview' } }],
    ['GET', `${BASE_URL}/api/connections`, null, { tags: { name: 'connections' } }],
    ['GET', `${BASE_URL}/api/pipelines`, null, { tags: { name: 'pipelines' } }],
    ['GET', `${BASE_URL}/api/health`, null, { tags: { name: 'health' } }],
    ['GET', `${BASE_URL}/api/snapshots`, null, { tags: { name: 'snapshots' } }],
    ['GET', `${BASE_URL}/api/sync/jobs`, null, { tags: { name: 'sync' } }],
  ]);

  responses.forEach((res) => {
    errorRate.add(res.status >= 400);
    check(res, { 'status is 2xx': (r) => r.status >= 200 && r.status < 300 });
  });

  sleep(1);
}

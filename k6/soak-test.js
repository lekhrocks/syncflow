import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10m', target: 50 },
    { duration: '60m', target: 50 },
    { duration: '10m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(90)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/api/health`);
  check(res, { 'health ok': (r) => r.status === 200 });
  sleep(5);
}

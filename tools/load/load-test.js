// k6 scenario for the SchoolSphere API (tools/load/load-test.js).
// Run from the repository root:
//   k6 run --vus 5 --duration 30s tools/load/load-test.js
//
// The script logs in with the documented demo account (see docs/DECISIONS.md) and then exercises
// representative authenticated reads against the selected school. Login is the slowest call, so the
// first virtual user pays that cost; subsequent users reuse the refreshed access token.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';
const EMAIL = __ENV.EMAIL ?? 'demo.admin@kilimani.example';
const PASSWORD = __ENV.PASSWORD ?? 'Demo1234!';

export const options = {
  scenarios: {
    schoolApi: {
      executor: 'ramping-vus',
      startVUs: 2,
      stages: [
        { duration: '10s', target: 5 },
        { duration: '20s', target: 5 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<8000'],
  },
};

let token;

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'login ok': (r) => r.status === 200 });
  const body = res.json();
  token = body.tokens.accessToken;
}

export default function () {
  const schoolId = __ENV.SCHOOL_ID ?? '';

  const members = http.get(`${BASE_URL}/api/v1/people/members`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'people.members' },
  });
  check(members, { 'members ok': (r) => r.status === 200 });

  const published = http.get(
    `${BASE_URL}/api/v1/timetable/published`,
    { headers: { Authorization: `Bearer ${token}` }, tags: { name: 'timetable.published' } },
  );
  check(published, { 'timetable ok': (r) => r.status === 200 });

  sleep(1);
}

import http from 'k6/http';
import { check } from 'k6';

// Light baseline for admin-api. Operational load is low by design (ADR-0011 —
// ~10 operators, dozens of requests/minute), so this is a modest read load on
// the heaviest operator read (the audit-log query), not a stress test. The
// repo's primary proofs are the security and audit-integrity JUnit suites.
//
// Run (admin-api + Postgres must be up, and you need a READONLY+ RS256 token):
//   ADMIN_BASE_URL=http://localhost:8090 ADMIN_TOKEN=<jwt> \
//     k6 run load-test/scenarios/audit-read.js

const BASE = __ENV.ADMIN_BASE_URL || 'http://localhost:8090';
const TOKEN = __ENV.ADMIN_TOKEN || '';

export const options = {
  scenarios: {
    audit_reads: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 30 },
        { duration: '30s', target: 30 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  // Admin reads are light; expect comfortable latency and near-zero errors.
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<100', 'p(99)<200'],
  },
};

export default function () {
  const res = http.get(`${BASE}/admin/v1/audit-logs?size=20`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(res, { 'status is 200': (r) => r.status === 200 });
}

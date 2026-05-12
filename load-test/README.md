# admin-api load test

> **Priority: low.** admin-api carries operational traffic only — roughly ten
> operators issuing dozens of requests per minute (ADR-0011). Throughput is not
> the point of this service; **security and audit integrity are**, and those are
> proven by the JUnit suites (`SecurityProbeTest`, `AdminSecurityTest`,
> `AuditIntegrityTest`), not by a load run.

This harness exists so the read path can be sanity-checked under a modest
concurrent load when a full stack is stood up.

## Scenario

`scenarios/audit-read.js` — ramps to 30 virtual users hitting the heaviest
operator read, `GET /admin/v1/audit-logs`, for 30s.

Thresholds (light, as befits the load profile):

| metric | target |
|---|---|
| error rate | < 1% |
| p95 latency | < 100 ms |
| p99 latency | < 200 ms |

## Running

Requires `k6`, a running `admin-api` + PostgreSQL, and a READONLY (or higher)
RS256 operator token whose public key admin-api trusts:

```sh
ADMIN_BASE_URL=http://localhost:8090 \
ADMIN_TOKEN="<operator-jwt>" \
  k6 run load-test/scenarios/audit-read.js
```

Curated results go under `results/<date>/`; the best run is tracked in
[`results/BEST.md`](results/BEST.md).

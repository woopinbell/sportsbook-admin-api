# Best results

| date | scenario | VUs | error rate | p95 | p99 | notes |
|---|---|---|---|---|---|---|
| — | audit-read | 30 | — | — | — | harness ready; not run in the dev session |

admin-api's load priority is intentionally low (ADR-0011 — operator traffic
only). No load run was recorded during development; the harness in
[`scenarios/audit-read.js`](scenarios/audit-read.js) is ready to execute against
a stood-up stack.

The proofs that matter for this service are green and reproducible in `mvn
verify`:

- **Security** — `SecurityProbeTest`: JWT `alg=none` rejected, RS256→HS256
  algorithm-confusion rejected, SQL-injection in the audit filter neutralized,
  disallowed IP blocked. `AdminSecurityTest`: forged / expired / wrong-key /
  tampered / missing token → 401, insufficient role → 403.
- **Audit integrity** — `AuditIntegrityTest` (real PostgreSQL + embedded Kafka):
  every action lands identically in `audit_log` and the `admin.action` topic,
  matched by action id; a failed downstream is recorded FAILED.

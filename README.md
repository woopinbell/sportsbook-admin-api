# admin-api

> **English summary**
>
> **What it is.** `admin-api` is the operator-facing REST entry point of the
> sportsbook microservice system. Staff (admins, traders, CS) call it to perform
> manual operations — void / replay a settlement, refund a wallet, change a
> user's risk limits, force-close or reopen a market — and to read the audit
> trail. It is deliberately separated from the user-facing `gateway` so that
> operator authorization, routing, and incident blast-radius stay isolated
> (ADR-0011).
>
> **Architecture (ADR-0011 — thin layer).** admin-api holds no business logic.
> It (1) verifies an operator JWT (RS256, `role` claim), (2) enforces an IP
> allowlist, (3) checks the role against the endpoint, then (4) delegates to the
> owning service's `/internal/v1` API over HTTP, propagating an
> `X-Admin-Actor-Id` / `-Role` / `-Action-Id` context plus the W3C trace
> context. The only datastore it owns is `audit_log`.
>
> **Roles.** `ADMIN` (everything), `TRADER` (settlement + market actions), `CS`
> (refunds + reads), `READONLY` (audit-log reads only). Enforced with Spring
> Security method `@PreAuthorize` guards.
>
> **Operations (delegation map).**
> | admin-api | → downstream (`/internal/v1`) |
> |---|---|
> | `POST /admin/v1/settlements/{betId}/void` | settlement-service |
> | `POST /admin/v1/settlements/replay/{eventId}` | settlement-service |
> | `POST /admin/v1/wallet/{userId}/refund` | wallet-service (credit) |
> | `PATCH /admin/v1/risk/users/{userId}/limits` | risk-service |
> | `POST /admin/v1/events/{eventId}/markets/{marketId}/close\|reopen` | odds-feed-service |
> | `GET /admin/v1/audit-logs?from=&to=&actor=` | local `audit_log` (offset page) |
>
> **Audit (ADR-0007).** Every action is dual-recorded — to a Kafka
> `admin.action` topic (`AdminActionRecorded` Avro) **and** the local `audit_log`
> table — so an incident can be cross-verified from two independent stores.
>
> **Tech stack.** Java 17, Spring Boot 3.2, Maven. Spring Security +
> OAuth2 Resource Server (RS256 JWT). `RestClient` to downstream services.
> PostgreSQL 16 + Flyway (`audit_log` only). Kafka + Avro (no schema registry in
> V1). Micrometer / OpenTelemetry / Prometheus.
>
> **Build & run.** `mvn verify` runs Spotless, Checkstyle and the test suite;
> integration tests use Testcontainers (Docker required) and WireMock (downstream
> stubs). `shared-protocol` 0.2.0 must be installed to mavenLocal first
> (`cd ../shared-protocol && mvn install`).
>
> **Security focus.** admin-api is the security-critical surface, so the proof
> emphasis is on authz and audit integrity rather than throughput: JWT tampering
> / expiry / insufficient-role → 401 / 403, IP-bypass attempts, SQL-injection
> attempts on the audit query, and a check that every action lands identically
> in Kafka and the DB. Operational load is low (~10 operators).
>
> **Limitations (V1).** Account suspend / unsuspend is **not exposed** — there is
> no user-service to delegate to in V1 and admin-api may not own business state
> (ADR-0011), so that ADR-0011 responsibility is left unimplemented. JWTs are
> verified, not issued (a separate IAM owns issuance; V1 supplies only the RSA
> public key via env var). See ADR-0011 / 0004 / 0007 in
> `orchestration/docs/architecture/decisions/`.
>
> **Docs.** Per-commit walkthroughs and the post-build retrospective live in
> [`docs/`](docs/README.md) once written.

---

## 시스템에서의 위치

`admin-api`는 9개 repo로 구성된 sportsbook 시스템의 **운영자 전용 진입점**이다.
사용자용 `gateway`와 완전히 분리되어, 운영자 인증·라우팅·사고 영향 범위를
격리한다 (분리 근거: [ADR-0011](../orchestration/docs/architecture/decisions/0011-admin-api-separation.md)).

```
운영자 (admin / trader / cs)
        │  Bearer <JWT, role claim> + 사내망 IP
        ▼
   ┌──────────┐   X-Admin-Actor-Id/Role/Action-Id + traceparent
   │ admin-api │ ─────────────────────────────────────────────►  각 service /internal/v1
   └────┬─────┘
        │ dual-write
        ├──► Kafka  admin.action  (AdminActionRecorded, Avro)
        └──► DB     audit_log
```

admin-api는 **thin layer**다. 비즈니스 로직을 직접 수행하지 않고, 인증·인가·
감사(audit)만 책임진 뒤 실제 작업은 그 도메인을 소유한 service의 internal API로
위임한다. admin-api가 직접 소유하는 영속 저장소는 `audit_log` 하나뿐이다.

## 책임 범위

**DO**
- 운영자 JWT 검증 (RS256, `role` claim) — 발급은 별도 IAM, V1은 공개키로 검증만
- IP allowlist (사내망/VPN — V1은 환경변수 CIDR)
- role 기반 endpoint 접근 제어 (`@PreAuthorize`)
- 운영 액션을 각 service `/internal/v1`로 위임하며 admin context 헤더 전파
- 모든 액션을 Kafka `admin.action` + DB `audit_log` 이중 박제

**DO NOT**
- 비즈니스 로직 직접 수행 (각 service로 위임)
- 사용자 endpoint 노출 / 사용자 인증 토큰 발급
- `audit_log` 외 영속 저장소 직접 접근

## role 모델

| role | 권한 |
|---|---|
| `ADMIN` | 전체 |
| `TRADER` | 정산(void/replay) + 마켓(close/reopen) |
| `CS` | 환불 + 조회 |
| `READONLY` | 감사 로그 조회만 |

## endpoint (위임 맵)

| admin-api | 위임 대상 (`/internal/v1`) | 최소 role |
|---|---|---|
| `POST /admin/v1/settlements/{betId}/void` | settlement-service | TRADER |
| `POST /admin/v1/settlements/replay/{eventId}` | settlement-service | TRADER |
| `POST /admin/v1/wallet/{userId}/refund` | wallet-service (credit) | CS |
| `PATCH /admin/v1/risk/users/{userId}/limits` | risk-service | TRADER |
| `POST /admin/v1/events/{eventId}/markets/{marketId}/close\|reopen` | odds-feed-service | TRADER |
| `GET /admin/v1/audit-logs?from=&to=&actor=` | 로컬 `audit_log` (offset page) | READONLY |

> 계정 동결(suspend/unsuspend)은 V1 미노출 — 위임할 user-service가 없고,
> admin-api는 `audit_log` 외 영속 상태를 소유하지 않기 때문 (ADR-0011 책임표의
> 해당 항목은 미구현으로 남김).

## 빌드 / 실행 / 테스트

```sh
# 선행: shared-protocol 0.2.0 을 mavenLocal 에 설치
cd ../shared-protocol && mvn install

cd ../admin-api
mvn verify            # Spotless + Checkstyle + 테스트 (Testcontainers → Docker 필요)
mvn spotless:apply    # 포맷 자동 적용
mvn spring-boot:run   # 로컬 실행 (Postgres + Kafka 필요)
```

주요 환경변수: `ADMIN_JWT_PUBLIC_KEY`(RSA 공개키 PEM), `ADMIN_IP_ALLOWLIST`(CIDR),
`SETTLEMENT_BASE_URL` / `WALLET_BASE_URL` / `RISK_BASE_URL` / `ODDS_FEED_BASE_URL`,
`ADMIN_DB_*`, `ADMIN_KAFKA_BOOTSTRAP`.

## 성능 / 보안

운영 부하는 낮다 (운영자 ~10명, 분당 ~수십 건). 따라서 증명의 무게는 처리량이
아니라 **보안과 감사 무결성**에 둔다 — 자세한 수치는 dev 완료 후
[`load-test/results/BEST.md`](load-test/results/BEST.md)와 본 절에 박제한다.

## 의존 버전

- `shared-protocol`: 0.2.0
- 위임 대상: settlement-service / wallet-service / risk-service / odds-feed-service 의 `/internal/v1`

## 제한사항 (V1)

- 계정 suspend/unsuspend 미노출 (위 참조).
- JWT는 검증만, 발급 안 함 (별도 IAM).
- audit log는 Kafka + DB 이중 박제. Schema Registry는 V1 미사용 (ADR-0014).
- 운영 어드민 UI는 백엔드 포트폴리오 스코프 외 (ADR-0011, V2 후보).

자세한 결정 근거는 `orchestration/docs/architecture/decisions/` 의
ADR-0011 / 0004 / 0007 / 0005 / 0006 / 0014 / 0015 / 0016 참조.

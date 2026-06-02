# admin-api

`admin-api`는 스포츠북 시스템의 운영자 전용 REST API입니다. 사용자 요청을 받는
`gateway`와 분리되어 있으며, 운영자 인증과 권한 확인, 내부 서비스 호출, 작업 이력
기록을 담당합니다. 도메인 로직은 각 서비스가 처리하고, 이 애플리케이션이 직접
관리하는 데이터는 `audit_log`뿐입니다.

## 요청 처리 흐름

```text
운영자
  │  RS256 JWT + 허용된 네트워크
  ▼
admin-api
  ├─ 운영자 역할과 엔드포인트 권한 확인
  ├─ X-Admin-Actor-Id, X-Admin-Actor-Role, X-Admin-Action-Id 전달
  ├─ W3C trace context 전달
  ├─ 각 도메인 서비스의 /internal/v1 API 호출
  └─ audit_log와 Kafka admin.action에 작업 결과 기록
```

운영자 JWT의 `role` 클레임을 `ADMIN`, `TRADER`, `CS`, `READONLY` 중 하나로
해석합니다. `/admin/**` 요청은 IP 허용 목록도 통과해야 합니다. JWT는 이
애플리케이션에서 발급하지 않으며, 별도 IAM에서 발급한 토큰을 RSA 공개키로
검증합니다.

관리 작업에는 UUIDv7 형식의 action ID를 부여합니다. 같은 ID를 하위 서비스 요청,
PostgreSQL의 `audit_log`, Kafka의 `admin.action` 이벤트에 사용하므로 작업 결과를
두 저장소에서 대조할 수 있습니다. 권한 부족이나 하위 서비스 오류로 작업이
실패하면 결과를 `FAILED`로 기록합니다.

## 권한

| 역할 | 허용되는 작업 |
|---|---|
| `ADMIN` | 모든 운영 작업과 감사 로그 조회 |
| `TRADER` | 정산 취소·재처리, 리스크 한도 변경, 마켓 종료·재개, 감사 로그 조회 |
| `CS` | 지갑 환불과 감사 로그 조회 |
| `READONLY` | 감사 로그 조회 |

## API

| 메서드와 경로 | 처리 대상 | 필요한 권한 |
|---|---|---|
| `GET /admin/v1/whoami` | 검증된 운영자 ID와 역할 확인 | 인증된 운영자 |
| `POST /admin/v1/settlements/{betId}/void` | settlement-service | `ADMIN`, `TRADER` |
| `POST /admin/v1/settlements/replay/{eventId}` | settlement-service | `ADMIN`, `TRADER` |
| `POST /admin/v1/wallet/{userId}/refund` | wallet-service | `ADMIN`, `CS` |
| `PATCH /admin/v1/risk/users/{userId}/limits` | risk-service | `ADMIN`, `TRADER` |
| `POST /admin/v1/events/{eventId}/markets/{marketId}/close` | odds-feed-service | `ADMIN`, `TRADER` |
| `POST /admin/v1/events/{eventId}/markets/{marketId}/reopen` | odds-feed-service | `ADMIN`, `TRADER` |
| `GET /admin/v1/audit-logs?from=&to=&actor=&page=&size=` | 로컬 `audit_log` | 모든 역할 |

계정 정지와 해제는 제공하지 않습니다. 현재 시스템에는 이 작업을 위임할
user-service가 없으며, `admin-api`가 사용자 상태를 직접 소유하지 않기 때문입니다.

## 기술 구성

- Java 17, Spring Boot 3.2.11, Maven
- Spring Security, OAuth2 Resource Server, RS256 JWT
- Spring `RestClient`
- PostgreSQL 16, Spring Data JPA, Flyway
- Kafka, Avro
- Micrometer, OpenTelemetry, Prometheus
- Testcontainers, WireMock, embedded Kafka

`shared-protocol`의 `0.2.0-SNAPSHOT` 계약을 로컬 Maven 저장소에서 사용합니다.

## 빌드와 실행

먼저 같은 상위 디렉터리에 있는 `sportsbook-shared-protocol-fix`를 설치합니다.

```sh
(cd ../sportsbook-shared-protocol-fix && ./mvnw install)
./mvnw verify
```

`verify`는 코드 형식, Checkstyle, 단위 테스트와 통합 테스트를 함께 확인합니다.
통합 테스트에서 Testcontainers를 사용하므로 Docker가 실행 중이어야 합니다.

로컬에서 애플리케이션을 실행하려면 PostgreSQL과 Kafka가 필요합니다.

```sh
./mvnw spring-boot:run
```

코드 형식을 자동으로 맞추려면 다음 명령을 사용합니다.

```sh
./mvnw spotless:apply
```

## 설정

기본 HTTP 포트는 `8090`입니다. 주요 환경 변수는 다음과 같습니다.

| 환경 변수 | 용도 |
|---|---|
| `ADMIN_JWT_PUBLIC_KEY` | RS256 검증에 사용할 RSA 공개키 PEM. 필수 값입니다. |
| `ADMIN_JWT_ROLE_CLAIM` | 운영자 역할 클레임 이름. 기본값은 `role`입니다. |
| `ADMIN_JWT_ISSUER` | 토큰 발급자 검증 값입니다. 설정한 경우에만 확인합니다. |
| `ADMIN_IP_ALLOWLIST` | `/admin/**` 요청을 허용할 CIDR 목록입니다. |
| `ADMIN_DB_URL`, `ADMIN_DB_USER`, `ADMIN_DB_PASSWORD` | 감사 로그용 PostgreSQL 연결 정보입니다. |
| `ADMIN_KAFKA_BOOTSTRAP` | Kafka bootstrap server 주소입니다. |
| `ADMIN_AUDIT_TOPIC` | 감사 이벤트 토픽입니다. 기본값은 `admin.action`입니다. |
| `SETTLEMENT_BASE_URL` | settlement-service 주소입니다. |
| `WALLET_BASE_URL` | wallet-service 주소입니다. |
| `RISK_BASE_URL` | risk-service 주소입니다. |
| `ODDS_FEED_BASE_URL` | odds-feed-service 주소입니다. |

로컬 기본 IP 허용 목록에는 loopback과 RFC 1918 사설망이 포함됩니다. 운영
환경에서는 실제 사내망 또는 VPN 대역으로 제한해야 합니다.

## 보안과 감사 검증

운영자 수가 약 10명이고 요청량도 분당 수십 건 수준이므로, 처리량보다 인증·인가와
감사 기록의 일관성을 우선해 검증합니다.

| 검증 항목 | 기대 결과 | 테스트 |
|---|---|---|
| 토큰 누락, 만료, 위조, 다른 키 서명, 훼손, 잘못된 형식 | `401` | `AdminSecurityTest` |
| `alg=none` 토큰 | `401` | `SecurityProbeTest` |
| RSA 공개키를 HMAC 키로 사용한 RS256→HS256 혼동 시도 | `401` | `SecurityProbeTest` |
| 역할 권한 부족 | `403` | `AdminSecurityTest`, `DelegationTest` |
| 허용 목록 밖의 IP | `403` | `AdminSecurityTest`, `SecurityProbeTest` |
| 감사 로그의 `actor` 조건을 이용한 SQL injection | 조회 결과 없음 | `SecurityProbeTest` |
| DB와 Kafka의 action ID 및 내용 일치 | 양쪽 기록 일치 | `AuditIntegrityTest` |
| 하위 서비스 실패 | `FAILED` 감사 기록 생성 | `AuditIntegrityTest` |

`SecurityProbeTest`의 SQL injection 검사는 실제 PostgreSQL을 사용합니다.
`AuditIntegrityTest`는 PostgreSQL과 embedded Kafka에 기록된 결과를 action ID로
대조합니다. 하위 서비스 호출은 WireMock으로 검증합니다.

감사 로그 조회 경로를 위한 가벼운 k6 시나리오는
[`load-test/`](load-test/README.md)에 있습니다. 측정 결과가 기록되기 전이므로
현재 문서에는 성능 수치를 제시하지 않습니다.

## 현재 제한

- JWT는 검증만 하며 발급하지 않습니다.
- 계정 정지와 해제 API는 제공하지 않습니다.
- Kafka 이벤트는 Avro로 직렬화하지만 Schema Registry는 사용하지 않습니다.
- 운영자 UI는 포함하지 않습니다.

관련 설계 결정은
`../sportsbook-orchestration-fix/docs/architecture/decisions/`의 ADR-0004,
ADR-0005, ADR-0006, ADR-0007, ADR-0011, ADR-0014, ADR-0015, ADR-0016에서
확인할 수 있습니다.

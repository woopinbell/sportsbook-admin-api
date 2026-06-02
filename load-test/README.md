# admin-api 부하 테스트

이 디렉터리에는 감사 로그 조회 경로를 확인하는 k6 시나리오가 있습니다.
`admin-api`는 약 10명의 운영자가 분당 수십 건을 요청하는 서비스이므로, 대규모
트래픽 한계를 찾기 위한 스트레스 테스트보다는 예상 운영 부하에서 조회 경로가
안정적으로 동작하는지 확인하는 데 목적이 있습니다.

인증·인가와 감사 기록의 일관성은 부하 테스트가 아니라
`SecurityProbeTest`, `AdminSecurityTest`, `AuditIntegrityTest`에서 검증합니다.

## 시나리오

[`scenarios/audit-read.js`](scenarios/audit-read.js)는
`GET /admin/v1/audit-logs?size=20`을 호출합니다.

가상 사용자 수는 15초 동안 0명에서 30명으로 늘어나고, 30초 동안 유지한 뒤,
15초 동안 다시 0명으로 줄어듭니다.

| 지표 | 기준 |
|---|---|
| 요청 실패율 | 1% 미만 |
| p95 응답 시간 | 100ms 미만 |
| p99 응답 시간 | 200ms 미만 |

## 실행 방법

다음 항목이 준비되어 있어야 합니다.

- k6
- 실행 중인 `admin-api`와 PostgreSQL
- `admin-api`가 신뢰하는 RSA 공개키로 검증할 수 있는 `READONLY` 이상의 운영자 JWT

저장소 루트에서 다음 명령을 실행합니다.

```sh
ADMIN_BASE_URL=http://localhost:8090 \
ADMIN_TOKEN="<operator-jwt>" \
  k6 run load-test/scenarios/audit-read.js
```

측정 결과는 `results/<날짜>/`에 보관하고, 대표 결과는
[`results/BEST.md`](results/BEST.md)에 정리합니다.

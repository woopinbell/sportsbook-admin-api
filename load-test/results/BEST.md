# 대표 부하 테스트 결과

아직 기록된 실행 결과가 없습니다.

| 날짜 | 시나리오 | 최대 가상 사용자 | 요청 실패율 | p95 | p99 | 비고 |
|---|---|---:|---:|---:|---:|---|
| — | `audit-read` | 30 | — | — | — | 시나리오만 준비된 상태입니다. |

시나리오는 [`../scenarios/audit-read.js`](../scenarios/audit-read.js)에 있습니다.
실행 환경을 준비한 뒤 실제 측정값으로 이 표를 갱신해야 합니다.

이 서비스에서 우선 확인할 항목은 처리량보다 보안과 감사 기록입니다.

- `SecurityProbeTest`는 `alg=none`, RS256→HS256 알고리즘 혼동, 감사 로그 조건의
  SQL injection, 허용되지 않은 IP 요청을 검사합니다.
- `AdminSecurityTest`는 누락·만료·위조·다른 키 서명·훼손 토큰을 `401`로,
  권한이 부족한 요청을 `403`으로 거부하는지 검사합니다.
- `AuditIntegrityTest`는 실제 PostgreSQL과 embedded Kafka에 기록된 작업을
  action ID로 대조하고, 하위 서비스가 실패했을 때 `FAILED`로 기록되는지 검사합니다.

이 검증은 저장소 루트에서 다음 명령으로 실행할 수 있습니다.

```sh
./mvnw verify
```

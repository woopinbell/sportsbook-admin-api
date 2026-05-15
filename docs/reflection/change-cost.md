# 변경 비용 시뮬레이션 (change cost)

6~12개월 안에 현실적으로 들어올 변경 요청과, 그때 admin-api의 어디가 깨지고 어떻게 복구하는지. thin layer라 "깨지는 곳"이 대부분 한 군데에 몰려 있다는 게 핵심.

| 변경 요청 | 깨질 위치 | 복구 동선 | 비용 |
|---|---|---|---|
| **계정 suspend/unsuspend 추가** (user-service 도입) | 위임 대상 없음 → 신규 `UserClient` + 컨트롤러 + `@Audited` + role 가드 | `client/UserClient` 추가, `UserAdminController`에 `/admin/v1/users/{id}/suspend\|unsuspend`, `AdminAction`에 2개 enum, downstream base-url 1개. 패턴이 이미 5개 있으니 복붙 수준 | **S** (반나절) |
| **다운스트림 internal API 시그니처 변경** (예: refund에 reason 필드 추가) | 해당 `*Client` + payload record 1개 | wire shape mirror record(`WalletCreditPayload`)만 수정. 컨트롤러/aspect/audit 무영향 | **XS** (1~2시간) |
| **운영자별 rate limit 강제** (분당 60건, ADR-0011 명시) | 현재 미구현 — 필터 1개 신규 | `/admin/**`에 actor 기준 in-memory 또는 Redis 토큰버킷 필터를 IP 필터 뒤에 추가. 401/403처럼 RFC 7807로 429 렌더 | **S** (반나절) |
| **audit 전달 보장 강화** (best-effort → 무손실) | `AuditService`의 기록 시점/방식 | action 전 `PENDING` row → 후 `CONFIRMED` 갱신 + Kafka는 outbox로. 또는 `@Audited`를 다운스트림 호출 전후 2단계로. 스키마/aspect 손봐야 함 | **M** (1~2일) |
| **admin.action consumer 등장** (SIEM/알림 등 외부 소비) | 스키마 진화 안전성 | Avro지만 registry 없음(ADR-0014). consumer 생기면 호환성 깨질 위험 → Apicurio/Confluent registry 도입 + `.avsc` 호환 규칙 | **M** (2~3일, 대부분 인프라) |
| **role 모델 세분화** (예: TRADER를 정산/마켓으로 분리) | 각 컨트롤러의 `@PreAuthorize` 문자열 | `AdminRole` enum + 해당 `hasAnyRole(...)` 수정. 가드가 메서드에 흩어져 있어 grep 1회로 다 보임 | **XS~S** |

## 의도적으로 미룬 진화

- **rate limit**: CLAUDE.md엔 "분당 60건"이 박혀 있지만 V1 코드엔 없다. 운영자 수가 적어 우선순위를 뒤로 뒀다. 필터 1개라 들어올 때 싸다.
- **audit outbox**: 비-트랜잭션 best-effort의 갭(회고 §5)을 알고도 뒀다. 무손실 보장은 복잡도 대비 V1 가치가 낮다고 판단.
- **OffsetPage를 shared-protocol로 승격**: 지금은 admin-api 로컬 record. gateway 등에서 같은 offset 페이지가 필요해지면 shared-protocol로 올린다(여러 repo 동기 필요해 hub 작업).

## 재설계가 합리적인 임계점

- **admin endpoint가 ~20개를 넘고 위임 패턴이 다양해지면**: 지금은 "컨트롤러 → Client → RestClient" 단순 포워딩이라 보일러플레이트가 많다. 수가 커지면 선언적 라우팅(예: 매핑 테이블 기반 제너릭 프록시)으로 재설계할 가치가 생긴다. 단, 그 전엔 명시적 컨트롤러가 가독성·감사 매핑 면에서 낫다.
- **다운스트림 호출이 동기 체인으로 길어지면**: 현재는 1 admin 액션 = 1 다운스트림 호출. 여러 service를 순차로 부르는 복합 운영 액션이 생기면 부분 실패 보상(saga)이 필요해져, thin-proxy 가정이 깨진다.
- **admin.action을 실제로 소비하기 시작하면**: registry 없는 Avro의 한계가 즉시 비용이 된다(위 표). 그 시점이 ADR-0014의 "V2에 registry 도입" 트리거.

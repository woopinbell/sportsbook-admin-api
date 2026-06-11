# admin-api 커밋 문서

dev 커밋 1개 = 페이지 1개. 개발 흐름 순서. (retrospective 커밋 자체는 문서화 대상 아님.)

## 목차

| # | 커밋 | 내용 |
|---|---|---|
| [000](000.md) | `chore(project)` | 골격 — pom/checkstyle/logback/포트, CLAUDE.md git 제외 |
| [001](001.md) | `feat(auth)` | RS256 JWT 검증 + role 가드 + IP allowlist, RFC 7807 |
| [002](002.md) | `feat(api)` | 운영 endpoint를 /internal/v1로 위임 + admin context 헤더 + UUID v7 |
| [003](003.md) | `feat(audit)` | Kafka + audit_log 이중 박제(@Audited aspect), offset 조회 |
| [004](004.md) | `test(security)` | 알고리즘 혼동 / SQL injection / IP bypass 시도 |
| [005](005.md) | `test(load)` | audit read 경량 k6 harness(부하 후순위) |
| [006](006.md) | `docs(readme)` | 보안/성능 증명 섹션 |
| [007](007.md) | `build(maven)` | (phase 2) maven wrapper 체크인 — risk-service 동일본, 균일 빌드 |

> **phase 경계**: ~[006] + retrospective 메타 = phase 1. **[007]부터 phase 2**(후속 윈도우,
> 2026-06-11 시작, 시작 커밋 `d159090`) — 경계 규정은 commit-policy.md §날짜·배치(phase 단위).

## L3 빠른 참조 (외워서 설명 — 면접 핵심)

- **RS256 JWT 검증기 + `alg` 고정의 보안 의미** + role claim→`ROLE_*` 변환 + 필터체인 순서(IP→bearer→@PreAuthorize) — [001](001.md)
- **admin context 전파의 목적**(분산 추적성: 누가/액션/trace) + **UUID v7 구조와 영속 전 생성 이유** + ArgumentResolver 주입 — [002](002.md)
- **aspect `@Order(HIGHEST_PRECEDENCE)`로 권한 거부(403)까지 감사** + **AOP 인자 바인딩 함정→리플렉션 해결** + **best-effort 이중 박제 트레이드오프 + action id cross-verify** — [003](003.md)
- **RS256→HS256 알고리즘 혼동 공격 메커니즘과 방어**(공개키를 HMAC 비밀로; alg 고정으로 차단) — [004](004.md)

## L2 빠른 참조 (문서 보며 설명)

- 부모 POM/BOM이 의존 버전 정렬, actuator liveness/readiness 분리 — [000](000.md)
- RFC 7807 보안 에러 렌더, IP 필터가 `/admin/**`만 거는 이유, health `permitAll` — [001](001.md)
- RestClient 빈 구성·이름 기반 주입, 상태 relay vs 502/504, RFC 7807 advice — [002](002.md)
- Avro byte[] 발행·Schema Registry 없음(V1), offset vs cursor pagination, SpEL target/reason — [003](003.md)
- SQLi가 파라미터 바인딩으로 무력화되는 원리, IP 필터 위치 — [004](004.md)
- k6 threshold 의미, 부하 수치를 지어내지 않은 정직성 — [005](005.md)

## L1 (읽으면 됨)

각 페이지의 endpoint/role 표, 디렉터리 구조, 설정값.

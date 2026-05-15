# admin-api 회고 (retrospective)

> Phase 4, 9개 repo 중 7번째. settlement 다음, gateway 직전. dev 7커밋 + 부하/보안 증명까지의 회고.

## 1. 무엇을 만들었나

운영자(operator) 전용 REST 진입점. 사용자용 `gateway`와 분리해 권한·라우팅·사고 영향 범위를 격리한다([ADR-0011](../../../orchestration/docs/architecture/decisions/0011-admin-api-separation.md)). 핵심은 **자체 비즈니스 로직이 없는 thin layer**라는 점이다. admin-api가 하는 일은 네 가지로 압축된다:

1. **인증** — 운영자 JWT를 RS256으로 검증(공개키는 환경변수, 발급은 별도 IAM 가정).
2. **인가** — `role` claim → `ADMIN/TRADER/CS/READONLY` → endpoint별 `@PreAuthorize` 가드. 추가로 IP allowlist.
3. **위임** — 정산 void/replay, 환불, 한도 변경, 마켓 close/reopen을 각 service의 `/internal/v1`로 HTTP 포워딩하면서 `X-Admin-Actor-*` 컨텍스트 헤더를 전파.
4. **감사(audit)** — 모든 액션을 `audit_log` 테이블 + Kafka `admin.action`에 이중 박제.

실제로 admin-api가 "소유"하는 영속 상태는 `audit_log` 한 테이블뿐이다. 나머지는 전부 남의 도메인을 호출만 한다.

## 2. 시작 시점의 가설

- **"thin layer라 빠르게 끝난다."** 비즈니스 로직이 없으니 컨트롤러는 거의 포워딩 한 줄일 것이다.
- **"인증/인가가 코드의 대부분을 차지한다."** Spring Security 설정이 제일 무거울 것이다.
- **"다운스트림 계약은 그냥 그대로 호출하면 된다."** 이미 만들어진 service들의 `/internal/v1`을 호출만 하면 되니 인터페이스 고민은 없다.

## 3. 가설 vs 실제 — 어디서 시간을 잃었나

### (a) 다운스트림 계약이 CLAUDE.md/ADR과 달랐다 — 가장 큰 시간

가설 (3)이 가장 크게 빗나갔다. 실제 service들의 컨트롤러를 열어보니 admin-api가 부를 endpoint가 문서의 가정과 세 군데에서 어긋났다:

- **마켓 close/reopen**: ADR/CLAUDE.md는 `POST /admin/v1/markets/{marketId}/close`였지만, odds-feed의 실제 라우팅은 `/internal/v1/events/{eventId}/markets/{marketId}/close`라 **eventId가 필수**였다. → admin 경로를 `/admin/v1/events/{eventId}/markets/{marketId}/...`로 바꿔 1:1 대응.
- **환불**: wallet에는 `/refund`가 없고 `transactions/credit`만 있었다. 환불 = "house가 자금을 대는 credit"으로 매핑(`source=HOUSE_POOL`), action id를 `Idempotency-Key`로.
- **계정 동결(suspend/unsuspend)**: 위임할 user-service가 V1에 아예 없었다. → endpoint 자체를 노출하지 않기로 결정(미구현으로 남김).

교훈: **계약은 문서가 아니라 코드에서 확인해야 한다.** 시작 전 다운스트림 컨트롤러를 먼저 읽은 게 결과적으로 가장 가치 있는 시간이었다. (C/C++에서 헤더의 시그니처를 믿고 링크했다가 ABI가 다르면 깨지는 것과 같은 감각 — REST에선 "헤더"가 없으니 직접 봐야 한다.)

### (b) `@Audited` AOP aspect의 인자 바인딩 함정

감사를 모든 액션에 빠짐없이 걸기 위해 annotation 기반 AOP(`@Around("@annotation(audited)")` + `Audited audited` 파라미터 바인딩)를 썼다. 교과서적 패턴인데 런타임에 `IllegalStateException: Required to bind 2 arguments, but only bound 1 (JoinPointMatch was NOT bound)`로 전부 500이 났다. `argNames`를 명시해도 동일. 결국 **바인딩을 포기하고 advice 안에서 리플렉션으로 `@Audited`를 읽는 방식**(`MethodSignature.getMethod().getAnnotation(...)`)으로 바꿔 해결했다.

이건 Java/Spring 처음 학습자에게 전형적인 함정이었다. C/C++엔 이런 "프록시가 메서드를 감싸고 포인트컷이 파라미터를 바인딩한다"는 레이어가 없어서, 에러 메시지(JoinPointMatch)가 무슨 뜻인지 해석하는 데 시간이 들었다. 리플렉션 방식이 바인딩보다 오히려 단순하고 견고하다는 게 결론.

### (c) 테스트 컨텍스트 전략 — 빠른 슬라이스 vs 무거운 통합

인증/위임 테스트는 DB·Kafka가 필요 없어서 `spring.autoconfigure.exclude`로 그것들을 빼 빠르게(컨테이너 없이) 돌렸다. 그런데 감사 레이어를 추가하자 `AuditService`가 `AuditLogRepository`(JPA)와 Kafka publisher를 요구해서 그 가벼운 컨텍스트들이 부팅 실패했다. → 두 테스트에 `@MockBean`으로 감사 의존성을 끊고, 진짜 이중 박제는 `AuditIntegrityTest`(실제 PostgreSQL via Testcontainers + embedded Kafka)에서만 검증하도록 분리.

교훈: **"무엇을 증명하는 테스트인가"에 따라 컨텍스트 무게를 다르게 가져간다.** 보안 슬라이스는 가볍게, 무결성 증명은 진짜 인프라로.

### (d) 작은 마찰들

- Checkstyle `MagicNumber`가 `0x7L << 12` 같은 **필드 초기화식 안의 시프트 상수**를 잡았다(`ignoreFieldDeclaration`은 단일 리터럴만 무시). → 시프트 결과를 미리 계산한 단일 hex 리터럴 상수로 교체.
- aspect를 `@Order(HIGHEST_PRECEDENCE)`로 두어 `@PreAuthorize`보다 바깥에서 돌게 했다. 덕분에 **권한 거부(403)도 감사 대상**이 된다(보안상 중요 — "누가 못 할 일을 시도했나"). 이건 의도한 설계지만 ordering을 이해하고 나서야 가능했다.

## 4. 다시 한다면

- **다운스트림 계약을 먼저 표로 만들고 시작한다.** 이번엔 구현하면서 어긋남을 발견했는데, 처음 30분을 "admin endpoint ↔ 실제 internal endpoint" 매핑 표 작성에 쓰면 설계 결정(eventId, refund=credit, suspend 보류)을 코드 전에 끝낼 수 있었다.
- **감사는 annotation+aspect 방식을 유지하되, 바인딩이 아니라 처음부터 리플렉션으로.** 시행착오를 건너뛴다.
- **audit의 전달 보장**을 처음부터 명시적으로 설계한다. 지금은 "다운스트림 액션은 이미 일어났으니 audit 쓰기 실패는 로그+메트릭만"이라는 best-effort인데(아래 한계), 이 결정을 코드 주석이 아니라 ADR로 박았으면 더 깔끔했다.

## 5. 남은 한계 (의도적으로 닫지 않은 범위)

[ADR-0012](../../../orchestration/docs/architecture/decisions/0012-v1-scope-decisions.md) V1 스코프 결정과 연결된다.

- **감사 이중 박제는 비-트랜잭션 best-effort다.** admin-api는 다운스트림 service와 한 트랜잭션으로 묶이지 않는다. 그래서 액션이 다운스트림에서 일어난 *뒤* 감사를 기록하고, 기록 실패는 로그+메트릭(`admin.audit.write.failure`)으로만 알린다(응답은 성공으로). 결과적으로 "액션은 됐는데 감사가 빠진" 갭이 이론상 가능하다. 강하게 닫으려면 outbox 패턴(액션 전 PENDING 기록 → 후 확정)이 필요한데 V1 스코프 밖.
- **계정 suspend/unsuspend 미구현.** user-service가 없어 위임 대상이 없고, admin-api는 `audit_log` 외 상태를 소유하지 않는다는 원칙 때문. V2에서 user-service 도입 시 추가.
- **IP allowlist의 X-Forwarded-For 신뢰.** V1은 "외부 트래픽은 k8s NetworkPolicy로 차단되고 신뢰된 ingress만 XFF를 세팅한다"는 배포 가정 위에서 XFF를 신뢰한다. 그 경계 밖에선 XFF가 위조 가능(보안 테스트가 이 한계를 문서화).
- **부하 수치 미측정.** 운영 부하가 낮아(운영자 ~10명) k6 harness만 두고 실측은 후순위로 뒀다. 증명의 무게는 보안·감사 무결성에 실었다.
- **Schema Registry 없음.** `admin.action`은 Avro지만 V1은 registry 없이 .avsc만(ADR-0014). 게다가 V1엔 이 토픽의 consumer가 아직 없다 — DB가 1차 조회원, Kafka는 스트리밍 사본.
- **JWT는 검증만, 발급 안 함.** 별도 IAM 가정. V1은 공개키만 환경변수로 받는다.

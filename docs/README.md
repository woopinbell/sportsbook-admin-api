# admin-api 문서

이 repo의 학습·면접 준비용 문서 진입점.

## 구성

- **[commits/](commits/README.md)** — dev 커밋 1개 = 페이지 1개(9단 골격). 개발 흐름 속에서 기술을 설명. 면접 직전 복습은 `commits/README.md`의 **L3/L2 빠른 참조** 색인부터.
- **[reflection/](reflection/retrospective.md)** — 빌드 후 회고.
  - [retrospective.md](reflection/retrospective.md) — 5단 회고(무엇/가설/가설vs실제/다시한다면/남은한계).
  - [change-cost.md](reflection/change-cost.md) — 6~12개월 변경 비용 시뮬레이션.

## 빠른 길잡이

- "admin-api가 뭐 하는 서비스?" → 상위 [README.md](../README.md) 영문 박스.
- "보안 어떻게 보장?" → README "성능/보안" 매트릭스 + [commits/004.md](commits/004.md).
- "면접에서 5분 설명할 핵심?" → [commits/README.md](commits/README.md) L3 색인.
- "왜 이렇게 설계?" → 결정 근거는 `orchestration/docs/architecture/decisions/`의 ADR-0011/0004/0007.

> `docs/notes/`는 두지 않는다(Phase 2+ 정책, 2026-05-29). 토픽 reference 대신 개발 흐름(commits/) 안에서 학습한다.

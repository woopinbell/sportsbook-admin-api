# admin-api

`admin-api`는 운영자가 스포츠북 서비스를 관리할 때 사용하는 내부 API입니다. 인증과 권한을 확인한 뒤 실제 작업은 담당 서비스에 위임합니다.

## 현재 구현 범위

- Java 17과 Spring Boot 기반 프로젝트 구성
- RS256 JWT 검증
- 역할별 권한과 IP 허용 목록 적용

## 빌드

`shared-protocol`을 먼저 설치하고 Maven wrapper로 검증합니다.

```sh
(cd ../sportsbook-shared-protocol-fix && ./mvnw install)
./mvnw verify
```

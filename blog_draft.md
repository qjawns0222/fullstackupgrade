# [Backend] Flyway 도입: ddl-auto update의 위험성 제거

## 도입 배경
`spring.jpa.hibernate.ddl-auto: update` 옵션은 개발 편의성을 제공하지만, 프로덕션 환경에서는 스키마 불일치 및 데이터 손실 위험이 매우 크다. 더 이상 주먹구구식으로 DB를 관리할 수 없어 형상 관리를 위해 Flyway를 도입했다.

## 구현 내용
1. **Flyway 설정**: `build.gradle`에 `flyway-core`, `flyway-mysql` 의존성 추가. `application.yml`에서 ddl-auto를 `validate` 모드로 변경하여 스키마 정합성 검증을 강제했다.
2. **Migration Script**: `V1__init_schema.sql` 작성하여 초기 테이블 구조를 명시적으로 정의했다.
3. **Refactoring**: `User` 엔티티의 테이블명을 `users`로 변경했다. H2 및 MariaDB에서 예약어 충돌로 인한 DDL 오류를 방지하기 위함이다.
4. **Monitoring**: Actuator 엔드포인트를 통해 마이그레이션 상태를 노출하고, 프론트엔드 관리자 페이지에서 이를 확인할 수 있도록 구현했다.

## 트러블슈팅
테스트 환경(H2 MySQL Mode)에서 `User` 테이블 생성 시 문법 오류가 발생했다. SQL 표준 예약어 문제임을 확인하고 `users`로 테이블명을 변경하여 해결했다. 또한 `SchemaMigrationTest` 작성 중 H2가 `flyway_schema_history` 테이블을 대문자로 찾는 문제가 있어 쿼팅(`"`) 처리를 통해 해결했다.

## 결론
ORM이 제멋대로 DB 스키마를 변경하는 리스크를 제거했다. 스키마 변경 이력은 이제 코드로 관리된다. 래퍼런스는 공식 문서 봐라.

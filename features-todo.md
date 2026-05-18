# 구현 예정 기능 목록

이 파일은 aiblog-agent 스킬이 자동 관리한다.
- 스킬 실행 시 맨 위 항목을 가져와 구현
- 구현 완료 후 해당 항목 삭제
- 목록이 비면 코드베이스 재스캔 후 10개 새로 생성

## 생성일: 2026-04-21

---

1. **분산 잡 오케스트레이션 (Distributed Job Orchestration with DAG)**
   - 문제: Spring Batch 주간 트렌드 분석 Job이 단일 스텝 선형 실행이라 스텝 간 의존성·병렬 실행·실패 스텝 재시도 전략이 없음
   - 해결: Spring Batch Flow DSL로 DAG 형태 Step 의존성 정의 — 병렬 파티셔닝(PartitionStep), 조건 분기(JobExecutionDecider), 실패 스텝 자동 재시작
   - 신규 라이브러리: `org.springframework.boot:spring-boot-starter-batch` (이미 있음), Batch Flow API 활용

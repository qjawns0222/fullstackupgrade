# 구현 완료 기능 목록

이 파일은 구현이 완료될 때마다 자동으로 업데이트됩니다.
새 기능 구현 시작 전 이 파일을 읽고 중복 구현을 방지합니다.

---

2026-03-17 | Saga pattern | 분산 트랜잭션 보상 처리 (manual saga)
2026-03-XX | Circuit Breaker | Resilience4j 기반 회로 차단기
2026-03-XX | Distributed Locks | ShedLock 분산 락
2026-03-XX | Spring Retry | 데드락 복구 재시도
2026-03-XX | OCR Caching | Tesseract + Caffeine 캐시
2026-03-XX | Idempotency Keys | Redis 기반 멱등성 키
2026-03-XX | Spring Batch | 배치 태스크 센터
2026-03-XX | Spring Modulith | 이벤트 발행/구독
2026-03-XX | GraphQL DataLoader | N+1 방지 DataLoader
2026-03-30 | Two-Level Cache | L1 Caffeine + L2 Redis pub/sub 클러스터 캐시 무효화
2026-03-30 | SQL Query Monitor | datasource-proxy 슬로우 쿼리 및 N+1 감지
2026-03-30 | Signed Webhook | OkHttp3 + HMAC-SHA256 서명 웹훅 전송
2026-03-30 | Structured Logging | logstash-logback-encoder MDC 기반 JSON 로깅
2026-03-30 | JSON Schema Validation | networknt/json-schema-validator API 계약 검증
2026-03-30 | XSS Sanitizer | OWASP Java HTML Sanitizer 기반 정책형 XSS 방어 및 AOP 자동 적용

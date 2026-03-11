# [Fullstack] AI 분석 시스템의 생존 전략: Resilience4j와 실시간 모니터링 도입기

대규모 시스템에서 "장애는 피할 수 없는 상수"입니다. 특히 OCR이나 AI 분석 처럼 자원 소모가 크고 외부 I/O에 의존적인 기능은 아주 작은 네트워크 지연이나 라이브러리 부하에도 전체 시스템을 마비시킬 수 있습니다.

이번 미션에서는 기존의 취약했던 AI 분석 파이프라인에 **Resilience4j**를 도입하여 결함 내성(Fault Tolerance)을 확보하고, 이를 실시간으로 관측할 수 있는 **모니터링 대시보드**를 구축한 과정을 정리합니다.

## 1. 냉철한 진단: 왜 Resilience가 필요한가?

기존 코드는 S3 업로드/다운로드와 OCR 처리가 직렬로 연결되어 있었고, 어떠한 예외 처리나 재시도 로직도 없었습니다.
- **S3 장애 시**: 분석 요청은 즉시 실패하며, 사용자는 다시 처음부터 파일을 업로드해야 함.
- **OCR 부하 시**: 무제한으로 스레드가 생성되어 CPU/Memory 고갈 위험.
- **가시성 제로**: 운영자는 현재 서킷 브레이커가 열렸는지, 실패율이 얼마인지 알 방법이 없음.

## 2. 해결책: Resilience4j를 이용한 방어막 구축

### 서비스 격리 및 재시도 전략 (Backend)
`S3Service`에는 일시적 장애에 대응하기 위한 **Retry**와 안정성을 위한 **Circuit Breaker**를 적용했고, `OcrService`에는 CPU 자원 보호를 위한 **Bulkhead**를 도입했습니다.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      s3Service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 5s
  bulkhead:
    instances:
      ocrService:
        maxConcurrentCalls: 3
        maxWaitDuration: 2s
```

이제 OCR 작업은 동시에 최대 3개까지만 처리되며, 그 이상의 요청은 대기하거나 빠르게 거절되어 시스템 전체의 가용성을 유지합니다.

## 3. 관측 가능성 (Observability): 실시간 대시보드

대시보드가 없는 Resilience는 눈 감고 운전하는 것과 같습니다. Next.js와 Recharts를 사용하여 커스텀 모니터링 페이지를 구현했습니다.

- **상태 시각화**: 각 서비스의 Circuit Breaker 상태(CLOSED/OPEN), 실패율, 버퍼 사이즈를 직관적으로 표현.
- **트렌드 분석**: Recharts를 이용하여 실패율과 지연 시간을 실시간 차트로 렌더링.
- **인프라 통합**: Spring Boot Actuator의 메트릭 데이터를 활용하여 백엔드 실태를 프론트에서 실시간 파악.

## 4. 시니어 개발자의 교훈

단순히 "기능이 돌아간다"에 안주하는 것은 시니어의 태도가 아닙니다. 시스템이 "어떻게 죽을 것인가"를 고민하고, 우아하게 실패(Fail Gracefully)하도록 설계하는 것이 진정한 기술적 가치입니다. 이번 작업을 통해 AI 서비스의 안정성을 한 단계 격상시켰습니다.

---
**Tag**: #Resilience4j #SpringBoot #Kotlin #Nextjs #Monitoring #Fullstack #DevOps #Observability

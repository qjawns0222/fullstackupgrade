# [Fullstack] ArchUnit 도입 + 스프링 부트 아키텍처 강제하기

프로젝트가 커지면 필연적으로 아키텍처가 무너진다. '잠깐만 이렇게 해야지' 했던 코드들이 모여 끔찍한 스파게티 코드가 되고, 순환 참조의 늪에 빠지게 된다. 오늘은 이를 방지하기 위한 안전장치, **ArchUnit**을 도입했다.

## 왜 ArchUnit인가?
단순히 "Controller는 Service만 호출해야 해"라고 말로 정하는 건 의미가 없다. 개발자는 게으르고, 마감은 닥쳐오기 때문이다. ArchUnit은 이 규칙을 '테스트 코드'로 강제한다. 빌드가 깨지면 배포도 없다. 잔인하지만 이게 맞다.

## 적용한 규칙
1. **Layered Architecture**: Controller -> Service -> Repository 흐름을 강제했다. 역류는 허용하지 않는다.
2. **No Cyclic Dependencies**: 패키지 간 순환 참조를 막았다. (`slices().matching("..").should().beFreeOfCycles()`)
3. **Naming Convention**: Controller 클래스는 반드시 `Controller`로 끝나야 한다.

## 리팩토링 과정: 순환 참조의 악몽
막상 돌려보니 예상대로 터졌다. `dto` 패키지의 `ErrorResponse`가 `exception` 패키지의 `ErrorCode`를 알고 있고, `exception` 패키지의 핸들러가 다시 `dto`를 쓰는 구조였다.

```kotlin
// Before (Bad)
fun of(errorCode: ErrorCode): ErrorResponse { ... } // DTO가 Exception 패키지를 의존함
```

과감하게 끊어냈다. DTO는 데이터를 담는 그릇일 뿐, 도메인이나 에러 정의를 알 필요가 없다. 필요한 값(code, message)만 받도록 수정하고, 조립은 핸들러에게 위임했다.

또한, 귀찮다고 Controller 파일 안에 끼워 넣었던 `data class` DTO들도 전부 `dto` 패키지로 유배 보냈다.

## 결론
ArchUnit은 '잔소리하는 시니어 개발자'를 CI/CD 파이프라인에 심어두는 것과 같다. 초기 설정이 조금 귀찮을 수 있지만, 나중에 기술 부채로 파산하는 것보단 백배 낫다. 지금 당장 적용해라.

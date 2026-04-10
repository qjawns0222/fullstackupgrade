package com.example.demo.tracing

/**
 * 메서드에 붙이면 실행 시간을 측정해 SpanRecord로 저장한다.
 * @param name span 이름 (비어있으면 클래스.메서드 명 사용)
 * @param slowThresholdMs 이 값 이상이면 SLOW로 분류 (기본 500ms)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithSpan(
    val name: String = "",
    val slowThresholdMs: Long = 500L
)

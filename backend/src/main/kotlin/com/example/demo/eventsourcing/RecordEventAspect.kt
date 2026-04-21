package com.example.demo.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

@Aspect
@Component
class RecordEventAspect(
    private val store: DomainEventStore,
    private val objectMapper: ObjectMapper
) {
    private val parser = SpelExpressionParser()

    @Around("@annotation(recordEvent)")
    fun around(pjp: ProceedingJoinPoint, recordEvent: RecordEvent): Any? {
        val result = pjp.proceed()

        val sig = pjp.signature as MethodSignature
        val params = sig.parameterNames
        val args = pjp.args
        val ctx = StandardEvaluationContext().apply {
            params.forEachIndexed { i, name -> setVariable(name, args[i]) }
            setVariable("result", result)
        }

        val aggregateId = if (recordEvent.aggregateIdSpel.isNotBlank())
            parser.parseExpression(recordEvent.aggregateIdSpel).getValue(ctx)?.toString() ?: "unknown"
        else "unknown"

        val actor = if (recordEvent.actorSpel.isNotBlank())
            parser.parseExpression(recordEvent.actorSpel).getValue(ctx)?.toString()
        else null

        val payload = result ?: emptyMap<String, Any>()
        store.append(
            DomainEvent(
                aggregateType = recordEvent.aggregateType,
                aggregateId = aggregateId,
                eventType = recordEvent.eventType,
                eventPayload = objectMapper.writeValueAsString(payload),
                actor = actor
            )
        )
        return result
    }
}

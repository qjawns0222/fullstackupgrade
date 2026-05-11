package com.example.demo.audit

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@Component
class ReactiveAuditPipeline(private val auditLogStore: AuditLogStore) {

    private val log = LoggerFactory.getLogger(ReactiveAuditPipeline::class.java)

    private val sink = Sinks.many().multicast().onBackpressureBuffer<AuditLogMessage>(1000)

    val processedCount = AtomicLong(0)
    val droppedCount = AtomicLong(0)

    @PostConstruct
    fun start() {
        sink.asFlux()
            .onBackpressureBuffer(1000) { dropped ->
                droppedCount.incrementAndGet()
                log.warn("Audit message dropped due to backpressure: action={}", dropped.action)
            }
            .bufferTimeout(50, Duration.ofMillis(100))
            .filter { it.isNotEmpty() }
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                { batch -> processBatch(batch) },
                { err -> log.error("Audit pipeline error", err) }
            )
        log.info("ReactiveAuditPipeline started (buffer=1000, batch=50/100ms)")
    }

    fun emit(message: AuditLogMessage) {
        val result = sink.tryEmitNext(message)
        if (result.isFailure) {
            droppedCount.incrementAndGet()
            log.warn("Failed to emit audit message: action={}, result={}", message.action, result)
        }
    }

    fun stats(): PipelineStats = PipelineStats(
        processed = processedCount.get(),
        dropped = droppedCount.get()
    )

    @PreDestroy
    fun stop() {
        sink.tryEmitComplete()
    }

    private fun processBatch(batch: List<AuditLogMessage>) {
        try {
            val documents = batch.map { it.toDocument() }
            auditLogStore.saveAll(documents)
            processedCount.addAndGet(batch.size.toLong())
            log.debug("Batch saved: size={}, total={}", batch.size, processedCount.get())
        } catch (e: Exception) {
            log.error("Failed to save audit batch size={}", batch.size, e)
        }
    }

    private fun AuditLogMessage.toDocument() = AuditLogDocument(
        userId = userId,
        action = action,
        description = description,
        params = params,
        status = status,
        errorMessage = errorMessage,
        timestamp = timestamp
    )
}

data class PipelineStats(
    val processed: Long,
    val dropped: Long
)

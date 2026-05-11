package com.example.demo.audit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReactiveAuditPipelineTest {

    private lateinit var store: FakeAuditLogStore
    private lateinit var pipeline: ReactiveAuditPipeline

    @BeforeEach
    fun setUp() {
        store = FakeAuditLogStore()
        pipeline = ReactiveAuditPipeline(store)
        pipeline.start()
    }

    @Test
    fun `emit single message is saved via store`() {
        val msg = buildMessage("CREATE_RESUME", "SUCCESS")
        pipeline.emit(msg)
        Thread.sleep(300)
        assertEquals(1, store.saved.size)
        assertEquals("CREATE_RESUME", store.saved[0].action)
    }

    @Test
    fun `emit multiple messages are batched and saved`() {
        repeat(10) { i ->
            pipeline.emit(buildMessage("ACTION_$i", "SUCCESS"))
        }
        Thread.sleep(400)
        assertEquals(10, store.saved.size)
    }

    @Test
    fun `processedCount increments after save`() {
        pipeline.emit(buildMessage("DELETE", "SUCCESS"))
        Thread.sleep(300)
        assertEquals(1, pipeline.processedCount.get())
    }

    @Test
    fun `stats returns processed and dropped counts`() {
        pipeline.emit(buildMessage("VIEW", "SUCCESS"))
        Thread.sleep(300)
        val stats = pipeline.stats()
        assertEquals(1, stats.processed)
        assertEquals(0, stats.dropped)
    }

    @Test
    fun `failure in store does not crash pipeline`() {
        store.shouldFail = true
        pipeline.emit(buildMessage("FAIL_ACTION", "SUCCESS"))
        Thread.sleep(300)
        // pipeline stays alive
        store.shouldFail = false
        pipeline.emit(buildMessage("RECOVER_ACTION", "SUCCESS"))
        Thread.sleep(300)
        assertEquals(1, store.saved.size)
        assertEquals("RECOVER_ACTION", store.saved[0].action)
    }

    private fun buildMessage(action: String, status: String) = AuditLogMessage(
        userId = "user1",
        action = action,
        description = "test",
        params = "{}",
        status = status,
        timestamp = LocalDateTime.now()
    )
}

class FakeAuditLogStore : AuditLogStore {
    val saved = mutableListOf<AuditLogDocument>()
    var shouldFail = false

    override fun saveAll(documents: List<AuditLogDocument>) {
        if (shouldFail) throw RuntimeException("Simulated ES failure")
        saved.addAll(documents)
    }
}

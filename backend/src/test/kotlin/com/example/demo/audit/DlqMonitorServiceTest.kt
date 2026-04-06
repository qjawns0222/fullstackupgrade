package com.example.demo.audit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.Optional

class DlqMonitorServiceTest {

    private lateinit var dlqRepository: FakeDlqRepository
    private lateinit var auditLogRepository: AuditLogRepository
    private lateinit var service: DlqMonitorService

    @BeforeEach
    fun setUp() {
        dlqRepository = FakeDlqRepository()
        auditLogRepository = mock(AuditLogRepository::class.java)
        val rabbitTemplate = mock(RabbitTemplate::class.java)
        service = DlqMonitorService(dlqRepository, auditLogRepository, rabbitTemplate)
    }

    @Test
    fun `receiveDlqMessage should save message with PENDING status`() {
        val msg = AuditLogMessage(
            userId = "user1",
            action = "CREATE_RESUME",
            description = "failed",
            params = "{}",
            status = "FAILED",
            timestamp = LocalDateTime.now()
        )

        service.receiveDlqMessage(msg)

        assertThat(dlqRepository.saved).hasSize(1)
        assertThat(dlqRepository.saved[0].dlqStatus).isEqualTo(DlqStatus.PENDING)
        assertThat(dlqRepository.saved[0].userId).isEqualTo("user1")
    }

    @Test
    fun `retry should resolve message when ES save succeeds`() {
        val dlqMsg = DlqMessage(
            id = 1L,
            userId = "user1",
            action = "TEST",
            description = "desc",
            params = "{}",
            status = "FAILED",
            originalTimestamp = LocalDateTime.now()
        )
        dlqRepository.store[1L] = dlqMsg

        `when`(auditLogRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = service.retry(1L)

        assertThat(result.dlqStatus).isEqualTo(DlqStatus.RESOLVED)
        assertThat(result.resolvedAt).isNotNull()
        verify(auditLogRepository, times(1)).save(any())
    }

    @Test
    fun `retry should throw when message not found`() {
        assertThatThrownBy { service.retry(999L) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `discard should mark message as DISCARDED`() {
        val dlqMsg = DlqMessage(
            id = 2L,
            userId = "user2",
            action = "DELETE",
            description = "desc",
            params = "{}",
            status = "FAILED",
            originalTimestamp = LocalDateTime.now()
        )
        dlqRepository.store[2L] = dlqMsg

        val result = service.discard(2L)

        assertThat(result.dlqStatus).isEqualTo(DlqStatus.DISCARDED)
        assertThat(result.resolvedAt).isNotNull()
    }

    @Test
    fun `retryAll should process all PENDING messages`() {
        for (i in 1L..3L) {
            dlqRepository.store[i] = DlqMessage(
                id = i,
                userId = "user$i",
                action = "ACT$i",
                description = "desc",
                params = "{}",
                status = "FAILED",
                originalTimestamp = LocalDateTime.now()
            )
        }
        `when`(auditLogRepository.save(any())).thenAnswer { it.arguments[0] }

        val count = service.retryAll()

        assertThat(count).isEqualTo(3)
        verify(auditLogRepository, times(3)).save(any())
    }

    @Test
    fun `getStats should return correct counts`() {
        dlqRepository.store[1L] = DlqMessage(
            id = 1L, userId = "u1", action = "A", description = "d",
            params = "{}", status = "FAILED", originalTimestamp = LocalDateTime.now(),
            dlqStatus = DlqStatus.PENDING
        )
        dlqRepository.store[2L] = DlqMessage(
            id = 2L, userId = "u2", action = "B", description = "d",
            params = "{}", status = "FAILED", originalTimestamp = LocalDateTime.now(),
            dlqStatus = DlqStatus.RESOLVED
        )

        val stats = service.getStats()

        assertThat(stats.total).isEqualTo(2)
        assertThat(stats.pending).isEqualTo(1)
        assertThat(stats.resolved).isEqualTo(1)
    }

    // ---- Fake DlqRepository ----

    class FakeDlqRepository : DlqRepository {
        val store = mutableMapOf<Long, DlqMessage>()
        val saved = mutableListOf<DlqMessage>()

        override fun findByDlqStatus(status: DlqStatus, pageable: Pageable) =
            PageImpl(store.values.filter { it.dlqStatus == status })

        override fun countByDlqStatus(status: DlqStatus) =
            store.values.count { it.dlqStatus == status }.toLong()

        override fun getStats() = DlqStats(
            total = store.size.toLong(),
            pending = store.values.count { it.dlqStatus == DlqStatus.PENDING }.toLong(),
            resolved = store.values.count { it.dlqStatus == DlqStatus.RESOLVED }.toLong(),
            discarded = store.values.count { it.dlqStatus == DlqStatus.DISCARDED }.toLong()
        )

        override fun <S : DlqMessage> save(entity: S): S {
            store[entity.id] = entity
            saved.add(entity)
            return entity
        }

        override fun findById(id: Long): Optional<DlqMessage> = Optional.ofNullable(store[id])
        override fun findAll(pageable: Pageable) = PageImpl(store.values.toList())
        override fun findAll(): MutableList<DlqMessage> = store.values.toMutableList()
        override fun findAll(sort: org.springframework.data.domain.Sort): MutableList<DlqMessage> = store.values.toMutableList()
        override fun <S : DlqMessage> saveAll(entities: Iterable<S>): MutableList<S> = entities.map { save(it) }.toMutableList()
        override fun existsById(id: Long) = store.containsKey(id)
        override fun deleteById(id: Long) { store.remove(id) }
        override fun delete(entity: DlqMessage) { store.remove(entity.id) }
        override fun deleteAll() { store.clear() }
        override fun deleteAll(entities: Iterable<DlqMessage>) { entities.forEach { delete(it) } }
        override fun deleteAllById(ids: Iterable<Long>) { ids.forEach { store.remove(it) } }
        override fun count() = store.size.toLong()
        override fun findAllById(ids: Iterable<Long>): MutableList<DlqMessage> = ids.mapNotNull { store[it] }.toMutableList()
        override fun flush() {}
        override fun <S : DlqMessage> saveAndFlush(entity: S) = save(entity)
        override fun <S : DlqMessage> saveAllAndFlush(entities: Iterable<S>) = saveAll(entities)
        override fun deleteAllInBatch() { store.clear() }
        override fun deleteAllInBatch(entities: Iterable<DlqMessage>) { entities.forEach { delete(it) } }
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) { ids.forEach { store.remove(it) } }
        override fun getOne(id: Long) = store[id]!!
        override fun getById(id: Long) = store[id]!!
        override fun getReferenceById(id: Long) = store[id]!!
        override fun <S : DlqMessage> findOne(example: org.springframework.data.domain.Example<S>): Optional<S> = Optional.empty()
        override fun <S : DlqMessage> findAll(example: org.springframework.data.domain.Example<S>): MutableList<S> = mutableListOf()
        override fun <S : DlqMessage> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort): MutableList<S> = mutableListOf()
        override fun <S : DlqMessage> findAll(example: org.springframework.data.domain.Example<S>, pageable: Pageable) = PageImpl(emptyList<S>())
        override fun <S : DlqMessage> count(example: org.springframework.data.domain.Example<S>) = 0L
        override fun <S : DlqMessage> exists(example: org.springframework.data.domain.Example<S>) = false
        override fun <S : DlqMessage, R : Any> findBy(
            example: org.springframework.data.domain.Example<S>,
            queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>
        ): R = throw UnsupportedOperationException()
    }
}

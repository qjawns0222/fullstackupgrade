package com.example.demo.encryption

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

class KeyRotationSchedulerTest {

    private lateinit var scheduler: KeyRotationScheduler
    private lateinit var fakeRepository: FakeKeyRotationHistoryRepository
    private lateinit var initialHandle: KeysetHandle
    private val store = mutableMapOf<String, String>()
    private val redisKey = "tink:keyset:primary"

    @Suppress("UNCHECKED_CAST")
    @BeforeEach
    fun setup() {
        AeadConfig.register()
        initialHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)

        val baos = ByteArrayOutputStream()
        CleartextKeysetHandle.write(initialHandle, JsonKeysetWriter.withOutputStream(baos))
        store[redisKey] = baos.toString(Charsets.UTF_8)

        val valueOps = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        Mockito.`when`(valueOps.get(redisKey)).thenAnswer { store[redisKey] }
        // set(String, String) stub — 반환 없는 void 메서드
        Mockito.doAnswer { inv ->
            store[inv.getArgument<String>(0)] = inv.getArgument<String>(1)
            null
        }.`when`(valueOps).set(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )
        val redisTemplate = Mockito.mock(StringRedisTemplate::class.java)
        Mockito.`when`(redisTemplate.opsForValue()).thenReturn(valueOps)

        // TinkConfig를 익명 서브클래스로 override — Mockito 없이
        val testTinkConfig = object : TinkConfig(redisTemplate) {
            override fun saveKeysetToRedis(handle: KeysetHandle) {
                val b = ByteArrayOutputStream()
                CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(b))
                store[redisKey] = b.toString(Charsets.UTF_8)
            }
        }

        fakeRepository = FakeKeyRotationHistoryRepository()
        scheduler = KeyRotationScheduler(testTinkConfig, fakeRepository, redisTemplate, redisKey)
    }

    @Test
    fun `rotateNow adds new key and saves SUCCESS history`() {
        val result = scheduler.rotateNow()

        assertThat(result.status).isEqualTo(RotationStatus.SUCCESS)
        assertThat(result.keyCount).isGreaterThan(0)
        assertThat(fakeRepository.saved).hasSize(1)
        assertThat(fakeRepository.saved.first().status).isEqualTo(RotationStatus.SUCCESS)
    }

    @Test
    fun `rotateNow updates keyset in redis`() {
        val before = store[redisKey]
        scheduler.rotateNow()
        val after = store[redisKey]
        assertThat(after).isNotNull()
        assertThat(after).isNotEqualTo(before)
    }

    @Test
    fun `rotateNow saves rotation timestamp close to now`() {
        val before = LocalDateTime.now().minusSeconds(2)
        val result = scheduler.rotateNow()
        assertThat(result.rotatedAt).isAfter(before)
    }

    @Test
    fun `rotateNow rotated keyset still decrypts data encrypted with original key`() {
        val service = EncryptionService(initialHandle)
        val plaintext = "민감한 데이터"
        val encrypted = service.encrypt(plaintext)

        scheduler.rotateNow()

        val newJson = store[redisKey]!!
        val newHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(newJson))
        val newService = EncryptionService(newHandle)

        assertThat(newService.decrypt(encrypted)).isEqualTo(plaintext)
    }
}

class FakeKeyRotationHistoryRepository : KeyRotationHistoryRepository {
    val saved = mutableListOf<KeyRotationHistory>()
    private var idSeq = 1L

    @Suppress("UNCHECKED_CAST")
    override fun <S : KeyRotationHistory> save(entity: S): S {
        val field = KeyRotationHistory::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, idSeq++)
        saved.add(entity)
        return entity
    }

    override fun findAll() = saved.toList()
    override fun findAll(sort: org.springframework.data.domain.Sort) = saved.toList()
    override fun findById(id: Long) = saved.firstOrNull { it.id == id }
        ?.let { java.util.Optional.of(it) } ?: java.util.Optional.empty()
    override fun existsById(id: Long) = saved.any { it.id == id }
    override fun count() = saved.size.toLong()
    override fun deleteById(id: Long) { saved.removeIf { it.id == id } }
    override fun delete(entity: KeyRotationHistory) { saved.remove(entity) }
    override fun deleteAll() { saved.clear() }
    override fun deleteAll(entities: Iterable<KeyRotationHistory>) { entities.forEach { saved.remove(it) } }
    override fun deleteAllById(ids: Iterable<Long>) { ids.forEach { id -> saved.removeIf { it.id == id } } }
    override fun <S : KeyRotationHistory> saveAll(entities: Iterable<S>) = entities.map { save(it) }
    override fun findAllById(ids: Iterable<Long>) = saved.filter { ids.contains(it.id) }
    override fun flush() {}
    override fun <S : KeyRotationHistory> saveAndFlush(entity: S) = save(entity)
    override fun <S : KeyRotationHistory> saveAllAndFlush(entities: Iterable<S>) = saveAll(entities)
    override fun deleteAllInBatch(entities: Iterable<KeyRotationHistory>) = deleteAll(entities)
    override fun deleteAllByIdInBatch(ids: Iterable<Long>) = deleteAllById(ids)
    override fun deleteAllInBatch() = deleteAll()
    override fun getOne(id: Long) = findById(id).orElseThrow()
    override fun getById(id: Long) = findById(id).orElseThrow()
    override fun getReferenceById(id: Long) = findById(id).orElseThrow()
    override fun <S : KeyRotationHistory> findOne(example: org.springframework.data.domain.Example<S>) = java.util.Optional.empty<S>()
    override fun <S : KeyRotationHistory> findAll(example: org.springframework.data.domain.Example<S>) = emptyList<S>()
    override fun <S : KeyRotationHistory> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort) = emptyList<S>()
    override fun <S : KeyRotationHistory> findAll(example: org.springframework.data.domain.Example<S>, pageable: org.springframework.data.domain.Pageable) = org.springframework.data.domain.Page.empty<S>()
    override fun <S : KeyRotationHistory> count(example: org.springframework.data.domain.Example<S>) = 0L
    override fun <S : KeyRotationHistory> exists(example: org.springframework.data.domain.Example<S>) = false
    override fun <S : KeyRotationHistory, R : Any> findBy(
        example: org.springframework.data.domain.Example<S>,
        queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>
    ): R = throw UnsupportedOperationException()
    override fun findAll(pageable: org.springframework.data.domain.Pageable) = org.springframework.data.domain.Page.empty<KeyRotationHistory>()
}

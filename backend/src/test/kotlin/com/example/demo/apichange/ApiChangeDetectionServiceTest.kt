package com.example.demo.apichange

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ApiChangeDetectionServiceTest {

    private lateinit var snapshotRepository: FakeApiSnapshotRepository
    private lateinit var breakingChangeRepository: FakeApiBreakingChangeRepository
    private lateinit var service: ApiChangeDetectionService

    @BeforeEach
    fun setUp() {
        snapshotRepository = FakeApiSnapshotRepository()
        breakingChangeRepository = FakeApiBreakingChangeRepository()
        service = ApiChangeDetectionService(snapshotRepository, breakingChangeRepository, ObjectMapper())
    }

    @Test
    fun `첫 번째 스냅샷은 이전 버전 없이 저장된다`() {
        val specJson = minimalSpec("v1.0")
        val result = service.captureAndCompare(specJson, "v1.0")

        assertThat(result.oldVersion).isNull()
        assertThat(result.newVersion).isEqualTo("v1.0")
        assertThat(result.compatible).isTrue()
        assertThat(result.breakingChanges).isEmpty()
        assertThat(snapshotRepository.findTopByOrderByCreatedAtDesc()?.version).isEqualTo("v1.0")
    }

    @Test
    fun `동일한 스펙을 두 번 캡처하면 breaking change가 없다`() {
        val spec = minimalSpec("v1.0")
        service.captureAndCompare(spec, "v1.0")
        val result = service.captureAndCompare(spec, "v1.1")

        assertThat(result.compatible).isTrue()
        assertThat(result.breakingChanges).isEmpty()
        assertThat(breakingChangeRepository.findAll()).isEmpty()
    }

    @Test
    fun `엔드포인트가 삭제되면 ENDPOINT_REMOVED breaking change가 감지된다`() {
        val specWithEndpoint = specWithGetResumes("v1.0")
        val specWithoutEndpoint = minimalSpec("v1.1")

        service.captureAndCompare(specWithEndpoint, "v1.0")
        val result = service.captureAndCompare(specWithoutEndpoint, "v1.1")

        assertThat(result.compatible).isFalse()
        val endpointRemoved = result.breakingChanges.filter { it.changeType == "ENDPOINT_REMOVED" }
        assertThat(endpointRemoved).isNotEmpty()
    }

    @Test
    fun `getStats는 전체 breaking change 수와 타입별 통계를 반환한다`() {
        // 첫 스냅샷
        service.captureAndCompare(specWithGetResumes("v1.0"), "v1.0")
        // 엔드포인트 삭제 → breaking
        service.captureAndCompare(minimalSpec("v1.1"), "v1.1")

        val stats = service.getStats()
        assertThat(stats.totalBreakingChanges).isGreaterThan(0)
        assertThat(stats.latestSnapshot).isEqualTo("v1.1")
        assertThat(stats.byType).containsKey("ENDPOINT_REMOVED")
    }

    @Test
    fun `getAllSnapshots는 최신순으로 반환한다`() {
        service.captureAndCompare(minimalSpec("v1.0"), "v1.0")
        service.captureAndCompare(minimalSpec("v1.1"), "v1.1")

        val snapshots = service.getAllSnapshots()
        assertThat(snapshots).hasSize(2)
        assertThat(snapshots.first().version).isEqualTo("v1.1")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun minimalSpec(version: String) = """
        {
          "openapi": "3.0.1",
          "info": { "title": "Test API", "version": "$version" },
          "paths": {}
        }
    """.trimIndent()

    private fun specWithGetResumes(version: String) = """
        {
          "openapi": "3.0.1",
          "info": { "title": "Test API", "version": "$version" },
          "paths": {
            "/api/resumes": {
              "get": {
                "operationId": "listResumes",
                "responses": { "200": { "description": "OK" } }
              }
            }
          }
        }
    """.trimIndent()
}

// ── Fake repositories ─────────────────────────────────────────────────────────

class FakeApiSnapshotRepository : ApiSnapshotRepository {
    private val store = mutableListOf<ApiSnapshot>()
    private var idSeq = 1L

    override fun findTopByOrderByCreatedAtDesc(): ApiSnapshot? =
        store.maxByOrNull { it.id }

    override fun findAllOrderByCreatedAtDesc(): List<ApiSnapshot> =
        store.sortedByDescending { it.id }

    override fun <S : ApiSnapshot> save(entity: S): S {
        val withId = ApiSnapshot(
            id = idSeq++,
            version = entity.version,
            specJson = entity.specJson,
            createdAt = entity.createdAt
        )
        @Suppress("UNCHECKED_CAST")
        store.add(withId as ApiSnapshot)
        @Suppress("UNCHECKED_CAST")
        return withId as S
    }

    override fun <S : ApiSnapshot> saveAll(entities: Iterable<S>): List<S> = entities.map { save(it) }
    override fun findAll(): List<ApiSnapshot> = store.toList()
    override fun findById(id: Long) = store.firstOrNull { it.id == id }?.let { java.util.Optional.of(it) } ?: java.util.Optional.empty()
    override fun existsById(id: Long) = store.any { it.id == id }
    override fun count() = store.size.toLong()
    override fun deleteById(id: Long) { store.removeIf { it.id == id } }
    override fun delete(entity: ApiSnapshot) { store.remove(entity) }
    override fun deleteAll() { store.clear() }
    override fun deleteAll(entities: Iterable<ApiSnapshot>) { entities.forEach { delete(it) } }
    override fun deleteAllById(ids: Iterable<Long>) { ids.forEach { deleteById(it) } }
    override fun findAllById(ids: Iterable<Long>) = store.filter { it.id in ids.toList() }
    override fun flush() {}
    override fun <S : ApiSnapshot> saveAndFlush(entity: S) = save(entity)
    override fun <S : ApiSnapshot> saveAllAndFlush(entities: Iterable<S>) = saveAll(entities)
    override fun deleteAllInBatch(entities: Iterable<ApiSnapshot>) = deleteAll(entities)
    override fun deleteAllByIdInBatch(ids: Iterable<Long>) = deleteAllById(ids)
    override fun deleteAllInBatch() = deleteAll()
    override fun getOne(id: Long) = findById(id).orElseThrow()
    override fun getById(id: Long) = findById(id).orElseThrow()
    override fun getReferenceById(id: Long) = findById(id).orElseThrow()
    override fun <S : ApiSnapshot> findOne(example: org.springframework.data.domain.Example<S>) = java.util.Optional.empty<S>()
    override fun <S : ApiSnapshot> findAll(example: org.springframework.data.domain.Example<S>) = emptyList<S>()
    override fun <S : ApiSnapshot> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort) = emptyList<S>()
    override fun <S : ApiSnapshot> findAll(example: org.springframework.data.domain.Example<S>, pageable: org.springframework.data.domain.Pageable) = org.springframework.data.domain.Page.empty<S>()
    override fun <S : ApiSnapshot> count(example: org.springframework.data.domain.Example<S>) = 0L
    override fun <S : ApiSnapshot> exists(example: org.springframework.data.domain.Example<S>) = false
    override fun <S : ApiSnapshot, R> findBy(example: org.springframework.data.domain.Example<S>, queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
    override fun findAll(sort: org.springframework.data.domain.Sort) = store.toList()
    override fun findAll(pageable: org.springframework.data.domain.Pageable) = org.springframework.data.domain.Page.empty<ApiSnapshot>()
}

class FakeApiBreakingChangeRepository : ApiBreakingChangeRepository {
    private val store = mutableListOf<ApiBreakingChange>()
    private var idSeq = 1L

    override fun findByOldVersionAndNewVersion(oldVersion: String, newVersion: String) =
        store.filter { it.oldVersion == oldVersion && it.newVersion == newVersion }

    override fun findAllByOrderByDetectedAtDesc() = store.sortedByDescending { it.detectedAt }

    override fun <S : ApiBreakingChange> save(entity: S): S {
        val withId = ApiBreakingChange(
            id = idSeq++,
            oldVersion = entity.oldVersion,
            newVersion = entity.newVersion,
            changeType = entity.changeType,
            description = entity.description,
            element = entity.element,
            detectedAt = entity.detectedAt
        )
        @Suppress("UNCHECKED_CAST")
        store.add(withId as ApiBreakingChange)
        @Suppress("UNCHECKED_CAST")
        return withId as S
    }

    override fun <S : ApiBreakingChange> saveAll(entities: Iterable<S>): List<S> = entities.map { save(it) }
    override fun findAll(): List<ApiBreakingChange> = store.toList()
    override fun findById(id: Long) = store.firstOrNull { it.id == id }?.let { java.util.Optional.of(it) } ?: java.util.Optional.empty()
    override fun existsById(id: Long) = store.any { it.id == id }
    override fun count() = store.size.toLong()
    override fun deleteById(id: Long) { store.removeIf { it.id == id } }
    override fun delete(entity: ApiBreakingChange) { store.remove(entity) }
    override fun deleteAll() { store.clear() }
    override fun deleteAll(entities: Iterable<ApiBreakingChange>) { entities.forEach { delete(it) } }
    override fun deleteAllById(ids: Iterable<Long>) { ids.forEach { deleteById(it) } }
    override fun findAllById(ids: Iterable<Long>) = store.filter { it.id in ids.toList() }
    override fun flush() {}
    override fun <S : ApiBreakingChange> saveAndFlush(entity: S) = save(entity)
    override fun <S : ApiBreakingChange> saveAllAndFlush(entities: Iterable<S>) = saveAll(entities)
    override fun deleteAllInBatch(entities: Iterable<ApiBreakingChange>) = deleteAll(entities)
    override fun deleteAllByIdInBatch(ids: Iterable<Long>) = deleteAllById(ids)
    override fun deleteAllInBatch() = deleteAll()
    override fun getOne(id: Long) = findById(id).orElseThrow()
    override fun getById(id: Long) = findById(id).orElseThrow()
    override fun getReferenceById(id: Long) = findById(id).orElseThrow()
    override fun <S : ApiBreakingChange> findOne(example: org.springframework.data.domain.Example<S>) = java.util.Optional.empty<S>()
    override fun <S : ApiBreakingChange> findAll(example: org.springframework.data.domain.Example<S>) = emptyList<S>()
    override fun <S : ApiBreakingChange> findAll(example: org.springframework.data.domain.Example<S>, sort: org.springframework.data.domain.Sort) = emptyList<S>()
    override fun <S : ApiBreakingChange> findAll(example: org.springframework.data.domain.Example<S>, pageable: org.springframework.data.domain.Pageable) = org.springframework.data.domain.Page.empty<S>()
    override fun <S : ApiBreakingChange> count(example: org.springframework.data.domain.Example<S>) = 0L
    override fun <S : ApiBreakingChange> exists(example: org.springframework.data.domain.Example<S>) = false
    override fun <S : ApiBreakingChange, R> findBy(example: org.springframework.data.domain.Example<S>, queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
    override fun findAll(sort: org.springframework.data.domain.Sort) = store.toList()
    override fun findAll(pageable: org.springframework.data.domain.Pageable) = org.springframework.data.domain.Page.empty<ApiBreakingChange>()
}

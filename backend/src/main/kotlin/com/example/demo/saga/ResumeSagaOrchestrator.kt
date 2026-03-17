package com.example.demo.saga

import com.example.demo.document.ResumeDocument
import com.example.demo.entity.Resume
import com.example.demo.entity.User
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.ResumeSearchRepository
import com.example.demo.shared.S3Service
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import java.time.Duration
import java.util.UUID
import org.springframework.beans.factory.annotation.Value

@Service
class ResumeSagaOrchestrator(
    private val s3Service: S3Service,
    private val s3Client: S3Client,
    private val resumeRepository: ResumeRepository,
    private val resumeSearchRepository: ResumeSearchRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    @Value("\${aws.s3.bucket}") private val bucket: String
) {
    private val log = LoggerFactory.getLogger(ResumeSagaOrchestrator::class.java)
    private val sagaCounter = meterRegistry.counter("resume.saga.started")
    private val sagaSuccessCounter = meterRegistry.counter("resume.saga.completed")
    private val sagaFailCounter = meterRegistry.counter("resume.saga.failed")
    private val sagaCompensatedCounter = meterRegistry.counter("resume.saga.compensated")

    private val SAGA_KEY_PREFIX = "saga:resume:"
    private val SAGA_TTL = Duration.ofHours(24)

    fun execute(file: MultipartFile, user: User): ResumeSagaResult {
        val sagaId = UUID.randomUUID().toString()
        val state = ResumeSagaState(
            sagaId = sagaId,
            userId = user.id!!,
            originalFileName = file.originalFilename ?: "unknown"
        )
        saveSagaState(state)
        sagaCounter.increment()
        log.info("[SAGA:{}] 시작 - 파일: {}", sagaId, state.originalFileName)

        // Step 1: S3 업로드
        val fileKey = try {
            val key = s3Service.uploadFile(file)
            state.s3FileKey = key
            state.status = ResumeSagaStatus.S3_UPLOADED
            saveSagaState(state)
            log.info("[SAGA:{}] Step1 완료 - S3 업로드: {}", sagaId, key)
            key
        } catch (e: Exception) {
            state.status = ResumeSagaStatus.FAILED
            state.errorMessage = "S3 업로드 실패: ${e.message}"
            saveSagaState(state)
            sagaFailCounter.increment()
            log.error("[SAGA:{}] Step1 실패 - S3 업로드 오류", sagaId, e)
            return ResumeSagaResult.failure(sagaId, state.errorMessage!!)
        }

        // Step 2: DB 저장
        val resume = try {
            val saved = saveToDatabase(file, user, fileKey)
            state.resumeId = saved.id
            state.status = ResumeSagaStatus.DB_SAVED
            saveSagaState(state)
            log.info("[SAGA:{}] Step2 완료 - DB 저장: resumeId={}", sagaId, saved.id)
            saved
        } catch (e: Exception) {
            state.status = ResumeSagaStatus.COMPENSATING
            state.errorMessage = "DB 저장 실패: ${e.message}"
            saveSagaState(state)
            log.error("[SAGA:{}] Step2 실패 - DB 저장 오류, 보상 트랜잭션 시작", sagaId, e)
            compensateS3(state, fileKey)
            sagaFailCounter.increment()
            return ResumeSagaResult.failure(sagaId, state.errorMessage!!)
        }

        // Step 3: Elasticsearch 인덱싱
        try {
            indexToElasticsearch(resume)
            state.status = ResumeSagaStatus.COMPLETED
            saveSagaState(state)
            sagaSuccessCounter.increment()
            log.info("[SAGA:{}] Step3 완료 - ES 인덱싱 성공, Saga 완료", sagaId)
        } catch (e: Exception) {
            // ES 인덱싱 실패는 보상 불필요 (eventually consistent) - 재시도 큐에 등록
            state.status = ResumeSagaStatus.COMPLETED
            state.errorMessage = "ES 인덱싱 실패(재시도 예정): ${e.message}"
            saveSagaState(state)
            scheduleEsRetry(state)
            sagaSuccessCounter.increment()
            log.warn("[SAGA:{}] Step3 경고 - ES 인덱싱 실패, 재시도 스케줄 등록", sagaId, e)
        }

        return ResumeSagaResult.success(sagaId, resume.id!!, fileKey)
    }

    @Transactional
    fun saveToDatabase(file: MultipartFile, user: User, fileKey: String): Resume {
        val resume = Resume(
            originalFileName = file.originalFilename ?: "unknown",
            content = fileKey,
            user = user
        )
        return resumeRepository.save(resume)
    }

    private fun indexToElasticsearch(resume: Resume) {
        val doc = ResumeDocument(
            id = resume.id!!,
            originalFileName = resume.originalFileName,
            content = resume.content,
            contentChosung = null
        )
        resumeSearchRepository.save(doc)
    }

    private fun compensateS3(state: ResumeSagaState, fileKey: String) {
        try {
            val deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .build()
            s3Client.deleteObject(deleteRequest)
            state.status = ResumeSagaStatus.COMPENSATED
            saveSagaState(state)
            sagaCompensatedCounter.increment()
            log.info("[SAGA:{}] 보상 완료 - S3 파일 삭제: {}", state.sagaId, fileKey)
        } catch (e: Exception) {
            state.status = ResumeSagaStatus.FAILED
            saveSagaState(state)
            log.error("[SAGA:{}] 보상 실패 - S3 파일 삭제 오류: {}", state.sagaId, fileKey, e)
        }
    }

    private fun scheduleEsRetry(state: ResumeSagaState) {
        val retryKey = "saga:es:retry:${state.sagaId}"
        redisTemplate.opsForValue().set(
            retryKey,
            objectMapper.writeValueAsString(state),
            Duration.ofHours(1)
        )
    }

    private fun saveSagaState(state: ResumeSagaState) {
        val key = "$SAGA_KEY_PREFIX${state.sagaId}"
        redisTemplate.opsForValue().set(
            key,
            objectMapper.writeValueAsString(state),
            SAGA_TTL
        )
    }

    fun getSagaState(sagaId: String): ResumeSagaState? {
        val key = "$SAGA_KEY_PREFIX$sagaId"
        val json = redisTemplate.opsForValue().get(key) ?: return null
        return objectMapper.readValue(json, ResumeSagaState::class.java)
    }

    fun getActiveSagaKeys(): List<String> {
        val pattern = "${SAGA_KEY_PREFIX}*"
        return redisTemplate.keys(pattern)?.toList() ?: emptyList()
    }
}

data class ResumeSagaResult(
    val sagaId: String,
    val success: Boolean,
    val resumeId: Long? = null,
    val fileKey: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(sagaId: String, resumeId: Long, fileKey: String) =
            ResumeSagaResult(sagaId, true, resumeId, fileKey)

        fun failure(sagaId: String, message: String) =
            ResumeSagaResult(sagaId, false, errorMessage = message)
    }
}

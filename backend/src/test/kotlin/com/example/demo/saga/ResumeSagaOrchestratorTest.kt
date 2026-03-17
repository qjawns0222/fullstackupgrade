package com.example.demo.saga

import com.example.demo.document.ResumeDocument
import com.example.demo.entity.Resume
import com.example.demo.entity.User
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.ResumeSearchRepository
import com.example.demo.shared.S3Service
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse

class ResumeSagaOrchestratorTest {

    private lateinit var s3Service: S3Service
    private lateinit var s3Client: S3Client
    private lateinit var resumeRepository: ResumeRepository
    private lateinit var resumeSearchRepository: ResumeSearchRepository
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var orchestrator: ResumeSagaOrchestrator
    private lateinit var file: MultipartFile
    private lateinit var user: User

    @BeforeEach
    fun setup() {
        s3Service = mock(S3Service::class.java)
        s3Client = mock(S3Client::class.java)
        resumeRepository = mock(ResumeRepository::class.java)
        resumeSearchRepository = mock(ResumeSearchRepository::class.java)
        redisTemplate = mock(StringRedisTemplate::class.java)
        valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
        file = mock(MultipartFile::class.java)

        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
        `when`(redisTemplate.keys(anyString())).thenReturn(emptySet())

        orchestrator = ResumeSagaOrchestrator(
            s3Service = s3Service,
            s3Client = s3Client,
            resumeRepository = resumeRepository,
            resumeSearchRepository = resumeSearchRepository,
            redisTemplate = redisTemplate,
            objectMapper = ObjectMapper(),
            meterRegistry = SimpleMeterRegistry(),
            bucket = "test-bucket"
        )

        user = User(id = 1L, username = "testuser", role = "ROLE_USER")
        `when`(file.originalFilename).thenReturn("resume.pdf")
        `when`(file.contentType).thenReturn("application/pdf")
        `when`(file.size).thenReturn(1024L)
    }

    @Test
    fun `모든 스텝 성공 시 COMPLETED 상태 반환`() {
        val fileKey = "uuid-resume.pdf"
        val savedResume = Resume("resume.pdf", fileKey, user).apply { id = 10L }

        `when`(s3Service.uploadFile(file)).thenReturn(fileKey)
        `when`(resumeRepository.save(any(Resume::class.java))).thenReturn(savedResume)
        `when`(resumeSearchRepository.save(any(ResumeDocument::class.java))).thenReturn(
            ResumeDocument(id = 10L, originalFileName = "resume.pdf", content = fileKey, contentChosung = null)
        )

        val result = orchestrator.execute(file, user)

        assertTrue(result.success)
        assertEquals(fileKey, result.fileKey)
        assertEquals(10L, result.resumeId)
        assertNull(result.errorMessage)
    }

    @Test
    fun `S3 업로드 실패 시 FAILED 반환 및 보상 없음`() {
        `when`(s3Service.uploadFile(file)).thenThrow(RuntimeException("S3 연결 실패"))

        val result = orchestrator.execute(file, user)

        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("S3 업로드 실패"))
        verify(resumeRepository, never()).save(any())
        verify(s3Client, never()).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest::class.java))
    }

    @Test
    fun `DB 저장 실패 시 S3 파일 삭제 보상 트랜잭션 실행`() {
        val fileKey = "uuid-resume.pdf"

        `when`(s3Service.uploadFile(file)).thenReturn(fileKey)
        `when`(resumeRepository.save(any(Resume::class.java))).thenThrow(RuntimeException("DB 저장 실패"))
        `when`(s3Client.deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest::class.java)))
            .thenReturn(DeleteObjectResponse.builder().build())

        val result = orchestrator.execute(file, user)

        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("DB 저장 실패"))
        // S3 보상 트랜잭션(deleteObject) 호출 검증
        verify(s3Client, times(1)).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest::class.java))
    }

    @Test
    fun `ES 인덱싱 실패 시 성공 반환 및 재시도 큐 등록`() {
        val fileKey = "uuid-resume.pdf"
        val savedResume = Resume("resume.pdf", fileKey, user).apply { id = 10L }

        `when`(s3Service.uploadFile(file)).thenReturn(fileKey)
        `when`(resumeRepository.save(any(Resume::class.java))).thenReturn(savedResume)
        `when`(resumeSearchRepository.save(any(ResumeDocument::class.java)))
            .thenThrow(RuntimeException("ES 연결 실패"))

        val result = orchestrator.execute(file, user)

        // ES 실패는 eventually consistent - 성공으로 처리
        assertTrue(result.success)
        assertEquals(10L, result.resumeId)
        // Redis에 재시도 키 등록 검증
        verify(valueOps, atLeast(1)).set(
            argThat { key: String -> key.startsWith("saga:es:retry:") },
            anyString(),
            any(java.time.Duration::class.java)
        )
    }

    @Test
    fun `Saga 상태가 Redis에 저장됨`() {
        val fileKey = "uuid-resume.pdf"
        val savedResume = Resume("resume.pdf", fileKey, user).apply { id = 10L }

        `when`(s3Service.uploadFile(file)).thenReturn(fileKey)
        `when`(resumeRepository.save(any(Resume::class.java))).thenReturn(savedResume)
        `when`(resumeSearchRepository.save(any(ResumeDocument::class.java))).thenReturn(
            ResumeDocument(id = 10L, originalFileName = "resume.pdf", content = fileKey, contentChosung = null)
        )

        orchestrator.execute(file, user)

        // saga:resume:{sagaId} 키로 상태가 저장됨 (최소 3회: STARTED, S3_UPLOADED, DB_SAVED, COMPLETED)
        verify(valueOps, atLeast(3)).set(
            argThat { key: String -> key.startsWith("saga:resume:") },
            anyString(),
            any(java.time.Duration::class.java)
        )
    }

    @Test
    fun `DB 저장 실패 후 S3 보상도 실패 시 FAILED 상태 처리`() {
        val fileKey = "uuid-resume.pdf"

        `when`(s3Service.uploadFile(file)).thenReturn(fileKey)
        `when`(resumeRepository.save(any(Resume::class.java))).thenThrow(RuntimeException("DB 실패"))
        `when`(s3Client.deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest::class.java)))
            .thenThrow(RuntimeException("S3 삭제도 실패"))

        val result = orchestrator.execute(file, user)

        assertFalse(result.success)
        // 예외가 전파되지 않고 실패 결과 반환
        assertNotNull(result.errorMessage)
    }
}

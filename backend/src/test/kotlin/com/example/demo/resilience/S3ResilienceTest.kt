package com.example.demo.resilience

import com.example.demo.shared.S3Service
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.ByteArrayInputStream

/**
 * Unit test for Resilience logic.
 * SpringBootTest was too heavy, so we manually apply decorators to verify behavior.
 */
class S3ResilienceTest {

    private val s3Client = mock(S3Client::class.java)
    private val s3Service = S3Service(s3Client, "test-bucket")

    @Test
    fun `should retry S3 upload on failure via manual decoration`() {
        // Given
        val file = mock(MultipartFile::class.java)
        `when`(file.originalFilename).thenReturn("fail.txt")
        `when`(file.inputStream).thenReturn(ByteArrayInputStream("content".toByteArray()))
        `when`(file.size).thenReturn(7L)
        `when`(file.contentType).thenReturn("text/plain")

        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(S3Exception.builder().message("Network Error").build())
            .thenReturn(PutObjectResponse.builder().build())

        // Resilience4j Retry instance
        val retry = Retry.ofDefaults("s3Service")

        // When
        val decorated = Retry.decorateFunction(retry) { f: MultipartFile -> s3Service.uploadFile(f) }
        decorated.apply(file)

        // Then
        // verify that the underlying client was called twice (once failed, once succeeded)
        verify(s3Client, times(2)).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `should open circuit breaker after failures`() {
        // Given
        val file = mock(MultipartFile::class.java)
        `when`(file.originalFilename).thenReturn("fail.txt")
        `when`(file.inputStream).thenReturn(ByteArrayInputStream("content".toByteArray()))
        
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(S3Exception.builder().message("Critical Error").build())

        val circuitBreaker = CircuitBreaker.ofDefaults("s3Service")
        val decorated = CircuitBreaker.decorateFunction(circuitBreaker) { f: MultipartFile -> s3Service.uploadFile(f) }

        // When
        repeat(100) {
            try { decorated.apply(file) } catch (e: Exception) {}
        }

        // Then
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }
}

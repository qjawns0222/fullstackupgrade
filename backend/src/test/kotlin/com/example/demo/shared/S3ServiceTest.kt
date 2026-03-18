package com.example.demo.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.io.ByteArrayInputStream
import software.amazon.awssdk.core.ResponseInputStream

@ExtendWith(MockitoExtension::class)
class S3ServiceTest {

    @Mock
    lateinit var s3Client: S3Client

    private lateinit var s3Service: S3Service

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        s3Service = S3Service(s3Client, "test-bucket")
    }

    @Test
    fun `should upload file successfully`() {
        // Given
        val file = org.mockito.Mockito.mock(MultipartFile::class.java)
        `when`(file.originalFilename).thenReturn("test.txt")
        `when`(file.inputStream).thenReturn(ByteArrayInputStream("content".toByteArray()))
        `when`(file.size).thenReturn(7L)
        `when`(file.contentType).thenReturn("text/plain")

        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().build())

        // When
        val result = s3Service.uploadFile(file)

        // Then
        assert(result.endsWith(".txt"))
    }
}

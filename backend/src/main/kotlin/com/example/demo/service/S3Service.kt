package com.example.demo.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
class S3Service(
    private val s3Client: S3Client,
    @Value("\${aws.s3.bucket}") private val bucket: String
) {
    private val log = LoggerFactory.getLogger(S3Service::class.java)

    fun uploadFile(file: MultipartFile): String {
        val originalName = file.originalFilename ?: "unknown"
        val extension = originalName.substringAfterLast(".", "")
        val fileKey = UUID.randomUUID().toString() + if (extension.isNotEmpty()) ".$extension" else ""

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(fileKey)
            .contentType(file.contentType ?: "application/octet-stream")
            .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.inputStream, file.size))
        log.info("File uploaded to S3/MinIO bucket {} with key {}", bucket, fileKey)

        return fileKey
    }

    fun downloadFile(fileKey: String): ByteArray {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(fileKey)
            .build()

        val response = s3Client.getObject(getObjectRequest)
        log.info("File downloaded from S3/MinIO bucket {} with key {}", bucket, fileKey)
        return response.readAllBytes()
    }
}

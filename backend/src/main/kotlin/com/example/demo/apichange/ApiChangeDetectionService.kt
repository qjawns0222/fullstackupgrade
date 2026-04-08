package com.example.demo.apichange

import com.fasterxml.jackson.databind.ObjectMapper
import org.openapitools.openapidiff.core.OpenApiCompare
import org.openapitools.openapidiff.core.model.ChangedOperation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApiChangeDetectionService(
    private val snapshotRepository: ApiSnapshotRepository,
    private val breakingChangeRepository: ApiBreakingChangeRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 현재 OpenAPI 스펙 JSON을 스냅샷으로 저장하고,
     * 직전 스냅샷과 비교해 Breaking Change를 DB에 기록한다.
     */
    @Transactional
    fun captureAndCompare(specJson: String, version: String): CompareResult {
        val previous = snapshotRepository.findTopByOrderByCreatedAtDesc()

        // 새 스냅샷 저장
        snapshotRepository.save(ApiSnapshot(version = version, specJson = specJson))

        if (previous == null) {
            log.info("[API-DIFF] 첫 스냅샷 저장 (version={})", version)
            return CompareResult(oldVersion = null, newVersion = version, breakingChanges = emptyList(), compatible = true)
        }

        log.info("[API-DIFF] 비교 시작: {} → {}", previous.version, version)

        val changed = OpenApiCompare.fromContents(previous.specJson, specJson)

        val breaking = mutableListOf<ApiBreakingChange>()

        if (changed.isIncompatible) {
            changed.changedOperations.forEach { op ->
                collectBreaking(op, previous.version, version, breaking)
            }
            // 삭제된 엔드포인트
            changed.missingEndpoints.forEach { ep ->
                breaking.add(
                    ApiBreakingChange(
                        oldVersion = previous.version,
                        newVersion = version,
                        changeType = "ENDPOINT_REMOVED",
                        description = "엔드포인트 삭제: ${ep.method.name} ${ep.pathUrl}",
                        element = "${ep.method.name} ${ep.pathUrl}"
                    )
                )
            }
            breakingChangeRepository.saveAll(breaking)
            log.warn("[API-DIFF] Breaking Change {} 건 감지", breaking.size)
        } else {
            log.info("[API-DIFF] 호환 변경만 있음")
        }

        return CompareResult(
            oldVersion = previous.version,
            newVersion = version,
            breakingChanges = breaking,
            compatible = changed.isCompatible
        )
    }

    private fun collectBreaking(
        op: ChangedOperation,
        oldVer: String,
        newVer: String,
        result: MutableList<ApiBreakingChange>
    ) {
        val endpoint = "${op.httpMethod.name} ${op.pathUrl}"

        // 필수 파라미터 추가
        op.parameters?.increased?.filter { it.required == true }?.forEach { param ->
            result.add(
                ApiBreakingChange(
                    oldVersion = oldVer, newVersion = newVer,
                    changeType = "PARAMETER_ADDED_REQUIRED",
                    description = "필수 파라미터 추가: ${param.name} (in ${param.`in`}) @ $endpoint",
                    element = endpoint
                )
            )
        }

        // 파라미터 삭제
        op.parameters?.missing?.forEach { param ->
            result.add(
                ApiBreakingChange(
                    oldVersion = oldVer, newVersion = newVer,
                    changeType = "PARAMETER_REMOVED",
                    description = "파라미터 삭제: ${param.name} (in ${param.`in`}) @ $endpoint",
                    element = endpoint
                )
            )
        }

        // 응답 스키마 변경 — apiResponses.changed: Map<String, ChangedResponse>
        op.apiResponses?.changed?.forEach { (code, changedResp) ->
            if (changedResp.content?.changed?.isNotEmpty() == true ||
                changedResp.content?.missing?.isNotEmpty() == true) {
                result.add(
                    ApiBreakingChange(
                        oldVersion = oldVer, newVersion = newVer,
                        changeType = "RESPONSE_SCHEMA_CHANGED",
                        description = "응답 스키마 변경: HTTP $code @ $endpoint",
                        element = endpoint
                    )
                )
            }
        }

        // 요청 바디 변경 — requestBody.content: ChangedContent
        op.requestBody?.let { rb ->
            if (rb.content?.changed?.isNotEmpty() == true ||
                rb.content?.missing?.isNotEmpty() == true ||
                rb.isChangeRequired) {
                result.add(
                    ApiBreakingChange(
                        oldVersion = oldVer, newVersion = newVer,
                        changeType = "REQUEST_BODY_CHANGED",
                        description = "요청 바디 스키마 변경 @ $endpoint",
                        element = endpoint
                    )
                )
            }
        }
    }

    fun getLatestSnapshot(): ApiSnapshot? = snapshotRepository.findTopByOrderByCreatedAtDesc()

    fun getAllSnapshots(): List<ApiSnapshot> = snapshotRepository.findAllOrderByCreatedAtDesc()

    fun getAllBreakingChanges(): List<ApiBreakingChange> =
        breakingChangeRepository.findAllByOrderByDetectedAtDesc()

    fun getBreakingChangesBetween(oldVersion: String, newVersion: String): List<ApiBreakingChange> =
        breakingChangeRepository.findByOldVersionAndNewVersion(oldVersion, newVersion)

    fun getStats(): ChangeStats {
        val all = breakingChangeRepository.findAll()
        val byType = all.groupBy { it.changeType }.mapValues { it.value.size }
        return ChangeStats(
            totalBreakingChanges = all.size,
            byType = byType,
            latestSnapshot = snapshotRepository.findTopByOrderByCreatedAtDesc()?.version
        )
    }
}

data class CompareResult(
    val oldVersion: String?,
    val newVersion: String,
    val breakingChanges: List<ApiBreakingChange>,
    val compatible: Boolean
)

data class ChangeStats(
    val totalBreakingChanges: Int,
    val byType: Map<String, Int>,
    val latestSnapshot: String?
)

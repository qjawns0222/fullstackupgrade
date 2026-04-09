package com.example.demo.apichange

import org.springframework.stereotype.Component

@Component
class JpaApiSnapshotStore(
    private val snapshotRepo: ApiSnapshotRepository
) : ApiSnapshotStore {

    override fun save(snapshot: ApiSnapshot) = snapshotRepo.save(snapshot)
    override fun findLatest() = snapshotRepo.findTopByOrderByCreatedAtDesc()
    override fun findAllDesc() = snapshotRepo.findAllOrderByCreatedAtDesc()
}

@Component
class JpaApiBreakingChangeStore(
    private val breakingRepo: ApiBreakingChangeRepository
) : ApiBreakingChangeStore {

    override fun saveAll(changes: List<ApiBreakingChange>) =
        breakingRepo.saveAll(changes).toList()

    override fun findAll() = breakingRepo.findAll()
    override fun findAllDesc() = breakingRepo.findAllByOrderByDetectedAtDesc()
    override fun findBetween(oldVersion: String, newVersion: String) =
        breakingRepo.findByOldVersionAndNewVersion(oldVersion, newVersion)
}

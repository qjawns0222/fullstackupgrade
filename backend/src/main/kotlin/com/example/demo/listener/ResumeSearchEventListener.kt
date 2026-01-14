package com.example.demo.listener

import com.example.demo.document.ResumeDocument
import com.example.demo.event.ResumeSearchEvent
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.ResumeSearchRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ResumeSearchEventListener(
        private val resumeRepository: ResumeRepository,
        private val resumeSearchRepository: ResumeSearchRepository
) {
        private val log = LoggerFactory.getLogger(ResumeSearchEventListener::class.java)

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        fun handleResumeSearchEvent(event: ResumeSearchEvent) {
                log.info("Starting Resume Search Indexing for Resume ID: {}", event.resumeId)

                val resume =
                        resumeRepository.findById(event.resumeId).orElseThrow {
                                RuntimeException("Resume not found for ID: ${event.resumeId}")
                        }

                val document =
                        ResumeDocument(
                                id = resume.id!!,
                                originalFileName = resume.originalFileName,
                                content = resume.content,
                                contentChosung =
                                        com.example.demo.util.HangulUtils.extractChosung(
                                                resume.originalFileName
                                        ) +
                                                " " +
                                                com.example.demo.util.HangulUtils.extractChosung(
                                                        resume.content
                                                )
                        )

                resumeSearchRepository.save(document)
                log.info("Indexed ResumeDocument for Resume ID: {}", event.resumeId)
        }
}

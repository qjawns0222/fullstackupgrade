package com.example.demo.config

import java.time.Duration
import org.springframework.context.annotation.Configuration
import org.springframework.modulith.events.CompletedEventPublications
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@Configuration
@EnableScheduling
class ModulithConfig {

    @Scheduled(fixedDelay = 60000)
    fun resubmitFailedEvents(incomplete: IncompleteEventPublications) {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5))
    }

    @Scheduled(fixedDelay = 3600000) // Every hour
    fun cleanUpCompletedEvents(completed: CompletedEventPublications) {
        completed.deletePublicationsOlderThan(Duration.ofDays(7))
    }
}

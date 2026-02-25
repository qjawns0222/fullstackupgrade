package com.example.demo.config

import com.example.demo.state.JobApplicationEvent
import com.example.demo.state.JobApplicationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [JobStateMachineConfig::class])
class JobStateMachineTest {

        @Autowired
        private lateinit var stateMachineFactory:
                StateMachineFactory<JobApplicationState, JobApplicationEvent>

        @Test
        fun `should transition from APPLIED to INTERVIEW`() {
                // Given
                val stateMachine = stateMachineFactory.getStateMachine("test-1")
                stateMachine.startReactively().block()

                // When
                stateMachine
                        .sendEvent(
                                Mono.just(
                                        MessageBuilder.withPayload(
                                                        JobApplicationEvent.START_INTERVIEW
                                                )
                                                .build()
                                )
                        )
                        .blockLast()

                // Then
                assertEquals(JobApplicationState.INTERVIEW, stateMachine.state.id)
        }

        @Test
        fun `should transition from INTERVIEW to OFFER_RECEIVED`() {
                // Given
                val stateMachine = stateMachineFactory.getStateMachine("test-2")
                stateMachine.startReactively().block()
                stateMachine
                        .sendEvent(
                                Mono.just(
                                        MessageBuilder.withPayload(
                                                        JobApplicationEvent.START_INTERVIEW
                                                )
                                                .build()
                                )
                        )
                        .blockLast()

                // When
                stateMachine
                        .sendEvent(
                                Mono.just(
                                        MessageBuilder.withPayload(
                                                        JobApplicationEvent.RECEIVE_OFFER
                                                )
                                                .build()
                                )
                        )
                        .blockLast()

                // Then
                assertEquals(JobApplicationState.OFFER_RECEIVED, stateMachine.state.id)
        }

        @Test
        fun `should block illegal transitions (eg APPLIED to PASS)`() {
                // Given
                val stateMachine = stateMachineFactory.getStateMachine("test-3")
                stateMachine.startReactively().block()

                // When
                stateMachine
                        .sendEvent(
                                Mono.just(
                                        MessageBuilder.withPayload(JobApplicationEvent.PASS).build()
                                )
                        )
                        .blockLast()

                // Then
                assertEquals(JobApplicationState.APPLIED, stateMachine.state.id)
        }
}

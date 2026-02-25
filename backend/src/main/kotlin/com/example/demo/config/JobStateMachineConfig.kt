package com.example.demo.config

import com.example.demo.state.JobApplicationEvent
import com.example.demo.state.JobApplicationState
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State

@Configuration
@EnableStateMachineFactory
class JobStateMachineConfig :
        EnumStateMachineConfigurerAdapter<JobApplicationState, JobApplicationEvent>() {

    private val log = LoggerFactory.getLogger(JobStateMachineConfig::class.java)

    override fun configure(
            config: StateMachineConfigurationConfigurer<JobApplicationState, JobApplicationEvent>
    ) {
        val listener =
                object : StateMachineListenerAdapter<JobApplicationState, JobApplicationEvent>() {
                    override fun stateChanged(
                            from: State<JobApplicationState, JobApplicationEvent>?,
                            to: State<JobApplicationState, JobApplicationEvent>
                    ) {
                        log.info("State changed from ${from?.id} to ${to.id}")
                    }
                }
        config.withConfiguration().autoStartup(false).listener(listener)
    }

    override fun configure(
            states: StateMachineStateConfigurer<JobApplicationState, JobApplicationEvent>
    ) {
        states.withStates()
                .initial(JobApplicationState.APPLIED)
                .states(EnumSet.allOf(JobApplicationState::class.java))
    }

    override fun configure(
            transitions: StateMachineTransitionConfigurer<JobApplicationState, JobApplicationEvent>
    ) {
        transitions
                .withExternal()
                .source(JobApplicationState.APPLIED)
                .target(JobApplicationState.INTERVIEW)
                .event(JobApplicationEvent.START_INTERVIEW)
                .and()
                .withExternal()
                .source(JobApplicationState.INTERVIEW)
                .target(JobApplicationState.OFFER_RECEIVED)
                .event(JobApplicationEvent.RECEIVE_OFFER)
                .and()
                .withExternal()
                .source(JobApplicationState.OFFER_RECEIVED)
                .target(JobApplicationState.PASSED)
                .event(JobApplicationEvent.PASS)
                .and()
                .withExternal()
                .source(JobApplicationState.APPLIED)
                .target(JobApplicationState.REJECTED)
                .event(JobApplicationEvent.REJECT)
                .and()
                .withExternal()
                .source(JobApplicationState.INTERVIEW)
                .target(JobApplicationState.REJECTED)
                .event(JobApplicationEvent.REJECT)
                .and()
                .withExternal()
                .source(JobApplicationState.OFFER_RECEIVED)
                .target(JobApplicationState.REJECTED)
                .event(JobApplicationEvent.REJECT)
    }
}

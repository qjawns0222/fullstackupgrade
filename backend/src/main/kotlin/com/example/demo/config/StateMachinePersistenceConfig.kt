package com.example.demo.config

import com.example.demo.state.JobApplicationEvent
import com.example.demo.state.JobApplicationState
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.statemachine.StateMachineContext
import org.springframework.statemachine.StateMachinePersist
import org.springframework.statemachine.persist.DefaultStateMachinePersister
import org.springframework.statemachine.persist.StateMachinePersister
import org.springframework.statemachine.data.redis.RedisStateMachineContextRepository
import org.springframework.statemachine.data.redis.RedisStateMachinePersister

import org.springframework.statemachine.persist.RepositoryStateMachinePersist

@Configuration
class StateMachinePersistenceConfig(private val redisConnectionFactory: RedisConnectionFactory) {

    @Bean
    fun stateMachinePersist(): StateMachinePersist<JobApplicationState, JobApplicationEvent, String> {
        val repository = RedisStateMachineContextRepository<JobApplicationState, JobApplicationEvent>(redisConnectionFactory)
        return RepositoryStateMachinePersist(repository)
    }

    @Bean
    fun persister(
        stateMachinePersist: StateMachinePersist<JobApplicationState, JobApplicationEvent, String>
    ): StateMachinePersister<JobApplicationState, JobApplicationEvent, String> {
        return DefaultStateMachinePersister(stateMachinePersist)
    }
}

package com.example.demo.graphql.dataloader

import com.example.demo.entity.User
import com.example.demo.repository.UserRepository
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class UserDataLoader(
    private val userRepository: UserRepository,
    registry: BatchLoaderRegistry
) {
    companion object {
        const val NAME = "userDataLoader"
    }

    init {
        registry.forTypePair(Long::class.javaObjectType, User::class.java)
            .withName(NAME)
            .registerMappedBatchLoader { ids, _ ->
                Mono.fromCallable {
                    userRepository.findAllById(ids).associateBy { it.id!! }
                }
            }
    }
}

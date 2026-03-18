package com.example.demo.graphql.query

import com.example.demo.entity.JobApplication
import com.example.demo.entity.User
import com.example.demo.graphql.dataloader.UserDataLoader
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.UserRepository
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

@Controller
class JobApplicationQueryController(
    private val jobApplicationRepository: JobApplicationRepository,
    private val userRepository: UserRepository
) {

    @QueryMapping
    fun myApplications(@AuthenticationPrincipal userDetails: UserDetails): List<JobApplication> {
        val user = userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }
        return jobApplicationRepository.findAllByUserId(user.id!!)
    }

    @QueryMapping
    fun application(
        @Argument id: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): JobApplication? {
        val user = userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }
        return jobApplicationRepository.findById(id)
            .filter { it.user.id == user.id }
            .orElse(null)
    }

    @SchemaMapping(typeName = "JobApplication", field = "user")
    fun user(
        application: JobApplication,
        env: DataFetchingEnvironment
    ): CompletableFuture<User> {
        val loader: DataLoader<Long, User> = env.getDataLoader(UserDataLoader.NAME)
        return loader.load(application.user.id!!)
    }
}

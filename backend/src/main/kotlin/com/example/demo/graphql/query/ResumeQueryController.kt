package com.example.demo.graphql.query

import com.example.demo.entity.Resume
import com.example.demo.entity.User
import com.example.demo.graphql.dataloader.UserDataLoader
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.UserRepository
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

@Controller
class ResumeQueryController(
    private val resumeRepository: ResumeRepository,
    private val userRepository: UserRepository
) {

    @QueryMapping
    fun resumes(@AuthenticationPrincipal userDetails: UserDetails): List<Resume> {
        val user = userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }
        return resumeRepository.findAllByUserId(user.id!!)
    }

    @SchemaMapping(typeName = "Resume", field = "user")
    fun user(resume: Resume, env: DataFetchingEnvironment): CompletableFuture<User> {
        val loader: DataLoader<Long, User> = env.getDataLoader(UserDataLoader.NAME)
        return loader.load(resume.user.id!!)
    }
}

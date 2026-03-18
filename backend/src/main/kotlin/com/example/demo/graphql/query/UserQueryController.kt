package com.example.demo.graphql.query

import com.example.demo.entity.JobApplication
import com.example.demo.entity.Resume
import com.example.demo.entity.User
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.UserRepository
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller

@Controller
class UserQueryController(
    private val userRepository: UserRepository,
    private val jobApplicationRepository: JobApplicationRepository,
    private val resumeRepository: ResumeRepository
) {

    @QueryMapping
    fun me(@AuthenticationPrincipal userDetails: UserDetails): User {
        return userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }
    }

    @SchemaMapping(typeName = "User", field = "applications")
    fun applications(user: User): List<JobApplication> =
        jobApplicationRepository.findAllByUserId(user.id!!)

    @SchemaMapping(typeName = "User", field = "resumes")
    fun resumes(user: User): List<Resume> =
        resumeRepository.findAllByUserId(user.id!!)
}

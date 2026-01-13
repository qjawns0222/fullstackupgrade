package com.example.demo.controller

import com.example.demo.entity.User
import com.example.demo.repository.PostRepository
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class TestController(
    private val postRepository: PostRepository
) {

    @GetMapping("/test")
    fun test(): String {
        return "Test Controller Working!"
    }

    @PostMapping("/getUser")
    fun getUser(@Valid @RequestBody user: User): List<User> {
        // throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return postRepository.searchByCondition()
    }
}

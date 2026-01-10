package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.PostRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class TestController {

    private final PostRepository postRepository;

    public TestController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/test")
    public String test() {
        return "Test Controller Working!";
    }

    @PostMapping("/getUser")
    public List<User> getUser(@Valid @RequestBody User user) {
        // throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return postRepository.searchByCondition();

    }
}

package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.entity.User;
import com.example.demo.repository.PostRepository;

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

    @GetMapping("/getUser")
    public List<User> getUser() {
        return postRepository.searchByCondition();
    }
}

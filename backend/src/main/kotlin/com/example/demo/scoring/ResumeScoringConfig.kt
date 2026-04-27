package com.example.demo.scoring

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ResumeScoringConfig {

    @Bean
    fun resumeScoringChatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem("You are an expert HR recruiter who evaluates resumes objectively and returns structured JSON.")
            .build()
}

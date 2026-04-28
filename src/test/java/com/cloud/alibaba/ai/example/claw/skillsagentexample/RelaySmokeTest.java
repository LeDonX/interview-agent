package com.cloud.alibaba.ai.example.claw.skillsagentexample;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
    class RelaySmokeTest {
        @Autowired
        private ChatModel chatModel;
        @Test
        void shouldCallRelay() {
            var response = chatModel.call(
                new Prompt("请只回复 OK",
                    ChatOptions.builder().temperature(0.1).build())
            );
            assertNotNull(response);
            assertNotNull(response.getResult());
            assertNotNull(response.getResult().getOutput().getText());
        }
    }
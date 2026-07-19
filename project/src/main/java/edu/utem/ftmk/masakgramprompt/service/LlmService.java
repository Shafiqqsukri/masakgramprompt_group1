package edu.utem.ftmk.masakgramprompt.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;


import java.time.Duration;
import java.util.List;


public class LlmService {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    public ChatModel buildModel(String modelTag) {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(modelTag)
                .timeout(Duration.ofMinutes(10))
                .build();
    }

    public String prompt(String modelTag, String systemPrompt, String userPrompt) {
        ChatModel model = buildModel(modelTag);

        List<ChatMessage> messages = List.of(
            SystemMessage.from(systemPrompt),
            UserMessage.from(userPrompt)
        );

        return model.chat(messages).aiMessage().text();
    }
}
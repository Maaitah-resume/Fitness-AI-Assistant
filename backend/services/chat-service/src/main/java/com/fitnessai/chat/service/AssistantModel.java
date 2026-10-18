package com.fitnessai.chat.service;

import com.fitnessai.chat.memory.ShortTermMemory.Turn;
import com.fitnessai.chat.model.ChatMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/** Thin seam around the LLM call, so the orchestration in ChatService is testable without OpenAI. */
@Component
public class AssistantModel {

    private final ChatClient chat;

    public AssistantModel(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public String reply(String systemPrompt, List<Turn> history, String userMessage, ChatTools tools) {
        List<Message> past = history.stream()
                .map(t -> t.role() == ChatMessage.Role.USER
                        ? (Message) new UserMessage(t.content())
                        : new AssistantMessage(t.content()))
                .toList();
        return chat.prompt()
                .system(systemPrompt)
                .messages(past)
                .user(userMessage)
                .tools(tools)
                .call()
                .content();
    }
}

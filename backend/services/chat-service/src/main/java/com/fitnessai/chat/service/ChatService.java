package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.RetrievedChunk;
import com.fitnessai.chat.dto.ChatDtos.RetrievedContext;
import com.fitnessai.chat.dto.ChatDtos.SendRequest;
import com.fitnessai.chat.dto.ChatDtos.SendResponse;
import com.fitnessai.chat.memory.LongTermMemoryExtractor;
import com.fitnessai.chat.memory.ShortTermMemory;
import com.fitnessai.chat.memory.ShortTermMemory.Turn;
import com.fitnessai.chat.model.ChatMessage.Role;
import com.fitnessai.chat.model.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One chat turn: resolve the conversation, gather context (short-term window,
 * profile, retrieval over documents + graph + long-term memories), call the
 * model with per-request tools, persist, and occasionally distil long-term
 * memories. Every dependency except the database is optional: if retrieval,
 * profile or the LLM fail, the user still gets a reply and nothing is lost.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    static final String FALLBACK_REPLY =
            "I'm having trouble reaching my AI engine right now. Your message is saved — please try again in a moment.";
    private static final int MEMORY_TRANSCRIPT_TURNS = 12;

    private final ConversationStore store;
    private final ShortTermMemory memory;
    private final LongTermMemoryExtractor longTermMemory;
    private final DownstreamClients clients;
    private final AssistantModel model;

    public ChatService(ConversationStore store, ShortTermMemory memory, LongTermMemoryExtractor longTermMemory,
                       DownstreamClients clients, AssistantModel model) {
        this.store = store;
        this.memory = memory;
        this.longTermMemory = longTermMemory;
        this.clients = clients;
        this.model = model;
    }

    public SendResponse send(long userId, String token, SendRequest req) {
        String message = req.message().strip();
        Conversation conversation = store.resolve(userId, req.chat_id(), message);
        long chatId = conversation.getId();

        // Read the window before persisting, so a cold cache rebuilt from the
        // database doesn't already contain the message we're about to send.
        List<Turn> history = memory.window(chatId);
        store.saveMessage(chatId, userId, Role.USER, message);
        Turn userTurn = new Turn(Role.USER, message);

        ProfileSnapshot profile = optional("profile lookup", () -> clients.myProfile(token), null);
        RetrievedContext context = optional("retrieval", () -> clients.retrieve(token, message), RetrievedContext.EMPTY);
        ChatTools tools = new ChatTools(token, clients);

        String reply;
        try {
            reply = model.reply(SystemPrompt.build(profile, context), history, message, tools);
        } catch (Exception e) {
            log.warn("LLM call failed for chat {}: {}", chatId, e.getMessage());
            memory.append(chatId, List.of(userTurn));
            return new SendResponse(FALLBACK_REPLY, chatId, tools.generatedFiles(), tools.profileUpdated(),
                    List.of(), true);
        }
        if (reply == null || reply.isBlank()) {
            reply = "I'm not sure how to answer that — could you rephrase?";
        }

        store.saveMessage(chatId, userId, Role.ASSISTANT, reply);
        Turn assistantTurn = new Turn(Role.ASSISTANT, reply);
        memory.append(chatId, List.of(userTurn, assistantTurn));

        if (LongTermMemoryExtractor.isDue(store.countUserMessages(chatId))) {
            List<Turn> recent = new ArrayList<>(history);
            recent.add(userTurn);
            recent.add(assistantTurn);
            longTermMemory.extractAndStore(token,
                    recent.subList(Math.max(0, recent.size() - MEMORY_TRANSCRIPT_TURNS), recent.size()));
        }

        return new SendResponse(reply, chatId, tools.generatedFiles(), tools.profileUpdated(),
                sources(context), false);
    }

    static List<String> sources(RetrievedContext context) {
        if (context == null || context.chunks() == null) {
            return List.of();
        }
        return context.chunks().stream()
                .map(RetrievedChunk::filename)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static <T> T optional(String what, Supplier<T> call, T fallback) {
        try {
            T result = call.get();
            return result == null ? fallback : result;
        } catch (Exception e) {
            log.warn("{} failed, continuing without it: {}", what, e.getMessage());
            return fallback;
        }
    }
}

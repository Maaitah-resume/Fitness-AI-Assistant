package com.fitnessai.chat.service;

import com.fitnessai.chat.model.ChatMessage;
import com.fitnessai.chat.model.Conversation;
import com.fitnessai.chat.repository.ChatMessageRepository;
import com.fitnessai.chat.repository.ConversationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * All chat persistence, in short transactions. Kept separate from ChatService
 * so the (slow) LLM call never runs inside a database transaction.
 */
@Component
public class ConversationStore {

    private static final int TITLE_MAX = 60;

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;

    public ConversationStore(ConversationRepository conversations, ChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public Conversation resolve(long userId, Long chatId, String firstMessage) {
        if (chatId != null) {
            return owned(userId, chatId);
        }
        return conversations.save(new Conversation(userId, title(firstMessage)));
    }

    @Transactional
    public Conversation create(long userId) {
        return conversations.save(new Conversation(userId, "New Chat"));
    }

    @Transactional
    public void saveMessage(long conversationId, long userId, ChatMessage.Role role, String content) {
        messages.save(new ChatMessage(conversationId, userId, role, content));
        conversations.findById(conversationId).ifPresent(c -> c.recordMessage(content));
    }

    @Transactional(readOnly = true)
    public long countUserMessages(long conversationId) {
        return messages.countByConversationIdAndRole(conversationId, ChatMessage.Role.USER);
    }

    @Transactional(readOnly = true)
    public List<Conversation> recent(long userId, int limit) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, limit));
    }

    /** Oldest-first, last {@code limit} messages. Caller must have checked access. */
    @Transactional(readOnly = true)
    public List<ChatMessage> messages(long conversationId, int limit) {
        return messages.findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, limit)).reversed();
    }

    @Transactional(readOnly = true)
    public Conversation owned(long userId, long chatId) {
        // 404 rather than 403 so other users' chat ids can't be probed.
        return conversations.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found."));
    }

    @Transactional(readOnly = true)
    public Conversation any(long chatId) {
        return conversations.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found."));
    }

    @Transactional
    public void delete(long userId, long chatId) {
        Conversation c = owned(userId, chatId);
        messages.deleteByConversationId(c.getId());
        conversations.delete(c);
    }

    /** Returns the deleted conversation ids so their cached windows can be cleared. */
    @Transactional
    public List<Long> deleteAllForUser(long userId) {
        List<Long> ids = conversations.findIdsByUserId(userId);
        messages.deleteByUserId(userId);
        conversations.deleteByUserId(userId);
        return ids;
    }

    static String title(String firstMessage) {
        String t = firstMessage.strip().replaceAll("\\s+", " ");
        return t.length() <= TITLE_MAX ? t : t.substring(0, TITLE_MAX - 3) + "...";
    }
}

package com.fitnessai.chat.controller;

import com.fitnessai.chat.dto.ApiResponse;
import com.fitnessai.chat.dto.ChatDtos.ConversationView;
import com.fitnessai.chat.dto.ChatDtos.MessageView;
import com.fitnessai.chat.dto.ChatDtos.MyChatStats;
import com.fitnessai.chat.dto.ChatDtos.SendRequest;
import com.fitnessai.chat.dto.ChatDtos.SendResponse;
import com.fitnessai.chat.memory.ShortTermMemory;
import com.fitnessai.chat.service.ChatService;
import com.fitnessai.chat.service.ChatStatsService;
import com.fitnessai.chat.service.ConversationStore;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final int RECENT_LIMIT = 30;
    private static final int HISTORY_LIMIT = 100;

    private final ChatService chat;
    private final ConversationStore store;
    private final ShortTermMemory memory;
    private final ChatStatsService stats;

    public ChatController(ChatService chat, ConversationStore store, ShortTermMemory memory, ChatStatsService stats) {
        this.chat = chat;
        this.store = store;
        this.memory = memory;
        this.stats = stats;
    }

    @PostMapping("/send")
    public ApiResponse<SendResponse> send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendRequest req) {
        return ApiResponse.ok(chat.send(userId(jwt), jwt.getTokenValue(), req));
    }

    @PostMapping("/new")
    public ApiResponse<Map<String, Long>> newChat(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(Map.of("chat_id", store.create(userId(jwt)).getId()));
    }

    @GetMapping("/recent")
    public ApiResponse<Map<String, List<ConversationView>>> recent(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(Map.of("chats",
                store.recent(userId(jwt), RECENT_LIMIT).stream().map(ConversationView::from).toList()));
    }

    @GetMapping("/{chatId}/messages")
    public ApiResponse<Map<String, List<MessageView>>> messages(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable long chatId) {
        store.owned(userId(jwt), chatId);
        return ApiResponse.ok(Map.of("messages",
                store.messages(chatId, HISTORY_LIMIT).stream().map(MessageView::from).toList()));
    }

    @DeleteMapping("/{chatId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long chatId) {
        store.delete(userId(jwt), chatId);
        memory.clear(List.of(chatId));
        return new ApiResponse<>("success", "Chat deleted.", null);
    }

    @GetMapping("/stats/me")
    public ApiResponse<MyChatStats> myStats(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(stats.forUser(userId(jwt)));
    }

    static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

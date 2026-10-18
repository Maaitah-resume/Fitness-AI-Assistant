package com.fitnessai.chat.controller;

import com.fitnessai.chat.dto.ApiResponse;
import com.fitnessai.chat.dto.ChatDtos.ChatAdminStats;
import com.fitnessai.chat.dto.ChatDtos.ConversationView;
import com.fitnessai.chat.dto.ChatDtos.MessageView;
import com.fitnessai.chat.dto.ChatDtos.UserActivity;
import com.fitnessai.chat.service.ChatStatsService;
import com.fitnessai.chat.service.ConversationStore;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only oversight of any user's conversations, for support and moderation. */
@RestController
@RequestMapping("/api/v1/chat/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ConversationStore store;
    private final ChatStatsService stats;

    public AdminChatController(ConversationStore store, ChatStatsService stats) {
        this.store = store;
        this.stats = stats;
    }

    @GetMapping("/users/{userId}/chats")
    public ApiResponse<Map<String, List<ConversationView>>> userChats(@PathVariable long userId) {
        return ApiResponse.ok(Map.of("chats",
                store.recent(userId, 100).stream().map(ConversationView::from).toList()));
    }

    @GetMapping("/chats/{chatId}/messages")
    public ApiResponse<Map<String, Object>> chatMessages(@PathVariable long chatId) {
        var conversation = store.any(chatId);
        return ApiResponse.ok(Map.of(
                "chat", ConversationView.from(conversation),
                "user_id", conversation.getUserId(),
                "messages", store.messages(chatId, 500).stream().map(MessageView::from).toList()));
    }

    @GetMapping("/stats")
    public ApiResponse<ChatAdminStats> stats() {
        return ApiResponse.ok(stats.global());
    }

    @GetMapping("/activity")
    public ApiResponse<Map<String, List<UserActivity>>> activity() {
        return ApiResponse.ok(Map.of("activity", stats.activityByUser()));
    }
}

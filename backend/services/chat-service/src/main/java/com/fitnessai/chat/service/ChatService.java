package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.Citation;
import com.fitnessai.chat.dto.ChatDtos.CoachPreferences;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
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
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    static final String RATE_LIMITED_REPLY =
            "I've hit the AI provider's usage limit for now. Your message is saved — please wait a minute and try again.";
    static final String FILES_READY_NOTE = " Your document was still created — it's attached below.";
    static final String FALLBACK_REPLY_AR =
            "أواجه صعوبة في الوصول إلى محرك الذكاء الاصطناعي الآن. تم حفظ رسالتك، حاول مرة أخرى بعد قليل.";
    static final String RATE_LIMITED_REPLY_AR =
            "وصلتُ إلى حد الاستخدام لدى مزوّد الذكاء الاصطناعي حاليًا. تم حفظ رسالتك، انتظر دقيقة ثم حاول مرة أخرى.";
    static final String FILES_READY_NOTE_AR = " تم إنشاء ملفك رغم ذلك، وهو مرفق بالأسفل.";
    private static final int MEMORY_TRANSCRIPT_TURNS = 12;

    private final ConversationStore store;
    private final ShortTermMemory memory;
    private final LongTermMemoryExtractor longTermMemory;
    private final DownstreamClients clients;
    private final AssistantModel model;
    private final PromptService prompts;
    private final WebSearch web;
    private final UserContextService userContext;

    public ChatService(ConversationStore store, ShortTermMemory memory, LongTermMemoryExtractor longTermMemory,
                       DownstreamClients clients, AssistantModel model, PromptService prompts, WebSearch web,
                       UserContextService userContext) {
        this.prompts = prompts;
        this.web = web;
        this.userContext = userContext;
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
        long userMessagesBefore = store.countAllUserMessages(userId);
        store.saveMessage(chatId, userId, Role.USER, message);
        Turn userTurn = new Turn(Role.USER, message);

        // All the user's app data, cached and refreshed every few messages (see UserContextService).
        UserContextService.UserContext site = userContext.get(userId, token, userMessagesBefore);
        ProfileSnapshot profile = site.profile();
        CoachPreferences preferences = site.preferences();
        String userData = site.userData();
        RetrievedContext context = optional("retrieval", () -> clients.retrieve(token, message), RetrievedContext.EMPTY);
        ChatTools tools = new ChatTools(token, clients, web);

        String reply;
        try {
            reply = model.reply(SystemPrompt.build(prompts.activeBase(), profile, preferences, userData, context, req.arabic()), history, message, tools);
        } catch (Exception e) {
            // Spring AI wraps provider errors ("Failed to generate content"); log the root cause too.
            String cause = NestedExceptionUtils.getMostSpecificCause(e).toString();
            log.warn("LLM call failed for chat {}: {} (root cause: {})", chatId, e.getMessage(), cause);
            memory.append(chatId, List.of(userTurn));
            // A 429 (quota/rate limit) is the common failure on free-tier keys; say so rather than "unreachable".
            boolean rateLimited = cause.contains("429") || cause.toLowerCase().contains("quota");
            String fallback = req.arabic()
                    ? (rateLimited ? RATE_LIMITED_REPLY_AR : FALLBACK_REPLY_AR)
                    : (rateLimited ? RATE_LIMITED_REPLY : FALLBACK_REPLY);
            // A tool may have already produced a file before the follow-up call failed.
            if (!tools.generatedFiles().isEmpty()) fallback += req.arabic() ? FILES_READY_NOTE_AR : FILES_READY_NOTE;
            return new SendResponse(fallback, chatId, tools.generatedFiles(), tools.profileUpdated(),
                    List.of(), true, tools.progressLogged(), List.of());
        }
        if (reply == null || reply.isBlank()) {
            reply = "I'm not sure how to answer that — could you rephrase?";
        }

        // Keep only the sources the reply actually cites (and drop markers that point nowhere).
        List<Citation> available = new ArrayList<>(Citations.documents(context).values());
        available.addAll(tools.webCitations());
        reply = Citations.stripUnknown(reply, available);
        List<Citation> cited = Citations.cited(reply, available);

        store.saveMessage(chatId, userId, Role.ASSISTANT, reply, Citations.toJson(cited));
        Turn assistantTurn = new Turn(Role.ASSISTANT, reply);
        memory.append(chatId, List.of(userTurn, assistantTurn));

        if (LongTermMemoryExtractor.isDue(store.countUserMessages(chatId))) {
            List<Turn> recent = new ArrayList<>(history);
            recent.add(userTurn);
            recent.add(assistantTurn);
            longTermMemory.extractAndStore(userId, token,
                    recent.subList(Math.max(0, recent.size() - MEMORY_TRANSCRIPT_TURNS), recent.size()));
        }

        // The coach changed the user's data: the next message must see it, not the cached snapshot.
        if (tools.profileUpdated() || tools.progressLogged() || !tools.generatedFiles().isEmpty()) {
            userContext.invalidate(userId);
        }

        return new SendResponse(reply, chatId, tools.generatedFiles(), tools.profileUpdated(),
                cited.stream().filter(c -> "document".equals(c.kind())).map(Citation::title).toList(),
                false, tools.progressLogged(), cited);
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

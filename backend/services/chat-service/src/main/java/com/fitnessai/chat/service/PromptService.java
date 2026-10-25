package com.fitnessai.chat.service;

import com.fitnessai.chat.model.PromptVersion;
import com.fitnessai.chat.repository.PromptVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * The admin-editable part of the coach's instructions. Only the base prompt is editable: profile,
 * preferences, training log, language and retrieved context are always appended by SystemPrompt,
 * so an edit can't switch off personalisation or the tools.
 */
@Service
public class PromptService {

    private final PromptVersionRepository versions;
    /** Read on every chat message; refreshed on each save, so no DB hit per message. */
    private volatile String active;

    public PromptService(PromptVersionRepository versions) {
        this.versions = versions;
    }

    public String activeBase() {
        String cached = active;
        if (cached == null) {
            cached = versions.findFirstByOrderByIdDesc().map(PromptVersion::getText).orElse(SystemPrompt.DEFAULT_BASE);
            active = cached;
        }
        return cached;
    }

    public Optional<PromptVersion> current() {
        return versions.findFirstByOrderByIdDesc();
    }

    public List<PromptVersion> history() {
        return versions.findTop30ByOrderByIdDesc();
    }

    public PromptVersion version(long id) {
        return versions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found."));
    }

    @Transactional
    public PromptVersion save(String text, long adminId, String adminName) {
        return store(text.strip(), adminId, adminName, "edit");
    }

    @Transactional
    public PromptVersion reset(long adminId, String adminName) {
        return store(SystemPrompt.DEFAULT_BASE, adminId, adminName, "reset");
    }

    @Transactional
    public PromptVersion restore(long id, long adminId, String adminName) {
        return store(version(id).getText(), adminId, adminName, "restore #" + id);
    }

    private PromptVersion store(String text, long adminId, String adminName, String action) {
        PromptVersion saved = versions.save(new PromptVersion(text, adminId, adminName, action));
        active = saved.getText();
        return saved;
    }
}

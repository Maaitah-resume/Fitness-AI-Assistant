package com.fitnessai.chat.controller;

import com.fitnessai.chat.dto.ApiResponse;
import com.fitnessai.chat.service.MealVision;
import com.fitnessai.chat.service.MealVision.MealEstimate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.util.Set;

/** Photo -> nutrition estimate. Nothing is stored here; the frontend logs the (edited) estimate to profile-service. */
@RestController
@RequestMapping("/api/v1/chat/meals")
public class MealController {

    private static final Logger log = LoggerFactory.getLogger(MealController.class);
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final MealVision vision;

    public MealController(MealVision vision) {
        this.vision = vision;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MealEstimate> analyze(@RequestPart("image") MultipartFile image,
                                             @RequestParam(defaultValue = "en") String language) throws IOException {
        if (image.isEmpty() || image.getContentType() == null || !TYPES.contains(image.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send a JPEG, PNG or WebP photo.");
        }
        if (image.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The photo is larger than 8 MB.");
        }
        try {
            MealEstimate estimate = vision.estimate(image.getBytes(), MimeType.valueOf(image.getContentType()), "ar".equals(language));
            if (estimate == null) throw new IllegalStateException("empty estimate");
            return ApiResponse.ok(estimate);
        } catch (Exception e) {
            String cause = NestedExceptionUtils.getMostSpecificCause(e).toString();
            log.warn("Meal photo analysis failed: {} (root cause: {})", e.getMessage(), cause);
            boolean limited = cause.contains("429") || cause.toLowerCase().contains("quota");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, limited
                    ? "The AI provider's usage limit was reached. Wait a minute and try again, or enter the meal manually."
                    : "The photo couldn't be analysed right now. Try again, or enter the meal manually.");
        }
    }
}

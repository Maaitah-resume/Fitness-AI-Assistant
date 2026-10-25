package com.fitnessai.profile.controller;

import com.fitnessai.profile.dto.ApiResponse;
import com.fitnessai.profile.model.BadgeDefinition;
import com.fitnessai.profile.model.BadgeRule;
import com.fitnessai.profile.repository.BadgeDefinitionRepository;
import com.fitnessai.profile.service.AchievementsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admins create, edit, switch off and delete badges; users earn them automatically by the badge's rule. */
@RestController
@RequestMapping("/api/v1/profile/admin/badges")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBadgeController {

    static final String ICONS = "dumbbell|calendar-check|medal|award|flame|trophy|weight|zap|scale|utensils|beef|"
            + "star|crown|target|heart|mountain|rocket|timer|footprints|bike|apple|droplet|shield|sparkles";
    static final String COLORS = "blue|green|yellow|red|steel|signal|ember";

    public record BadgeRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 60) String nameAr,
            @Size(max = 200) String description,
            @Size(max = 200) String descriptionAr,
            @NotBlank @Pattern(regexp = ICONS) String icon,
            @NotBlank @Pattern(regexp = COLORS) String color,
            @NotBlank String rule,
            @NotNull @DecimalMin("0.1") @DecimalMax("100000") Double threshold,
            @Size(max = 60) String exercise,
            Boolean active,
            @Min(0) @Max(100000) Integer sortOrder) {
    }

    public record BadgeAdminView(Long id, String code, String name, String nameAr, String description, String descriptionAr,
                                 String icon, String color, String rule, double threshold, String exercise, boolean active,
                                 int sortOrder, long earnedBy) {
        static BadgeAdminView from(BadgeDefinition d, long earnedBy) {
            return new BadgeAdminView(d.getId(), d.getCode(), d.getName(), d.getNameAr(), d.getDescription(), d.getDescriptionAr(),
                    d.getIcon(), d.getColor(), d.getRule().name().toLowerCase(Locale.ROOT), d.getThreshold(), d.getExercise(),
                    d.isActive(), d.getSortOrder(), earnedBy);
        }
    }

    /** What the admin form offers: the rules (with the threshold's unit), icons and colours. */
    public record RuleInfo(String key, String unit, boolean needsExercise) {
    }

    public record Options(List<RuleInfo> rules, List<String> icons, List<String> colors) {
    }

    private final BadgeDefinitionRepository definitions;
    private final AchievementsService achievements;

    public AdminBadgeController(BadgeDefinitionRepository definitions, AchievementsService achievements) {
        this.definitions = definitions;
        this.achievements = achievements;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        Map<String, Long> earned = achievements.earnedCounts();
        return ApiResponse.ok(Map.of("badges", definitions.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(d -> BadgeAdminView.from(d, earned.getOrDefault(d.getCode(), 0L))).toList()));
    }

    @GetMapping("/options")
    public ApiResponse<Options> options() {
        return ApiResponse.ok(new Options(
                Arrays.stream(BadgeRule.values()).map(r -> new RuleInfo(r.name().toLowerCase(Locale.ROOT), r.unit(), r.needsExercise())).toList(),
                List.of(ICONS.split("\\|")), List.of(COLORS.split("\\|"))));
    }

    @PostMapping
    @Transactional
    public ApiResponse<BadgeAdminView> create(@Valid @RequestBody BadgeRequest req) {
        BadgeDefinition d = new BadgeDefinition(uniqueCode(req.name()));
        apply(d, req);
        if (req.sortOrder() == null) {
            d.setSortOrder(definitions.findAll().stream().mapToInt(BadgeDefinition::getSortOrder).max().orElse(0) + 10);
        }
        return ApiResponse.ok(BadgeAdminView.from(definitions.save(d), 0));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<BadgeAdminView> update(@PathVariable long id, @Valid @RequestBody BadgeRequest req) {
        BadgeDefinition d = definitions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge not found"));
        apply(d, req);
        return ApiResponse.ok(BadgeAdminView.from(definitions.save(d), achievements.earnedCounts().getOrDefault(d.getCode(), 0L)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable long id) {
        if (!definitions.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge not found");
        definitions.deleteById(id);
        return ApiResponse.ok(null);
    }

    private static void apply(BadgeDefinition d, BadgeRequest req) {
        BadgeRule rule;
        try {
            rule = BadgeRule.valueOf(req.rule().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown rule: " + req.rule());
        }
        String exercise = blank(req.exercise());
        if (rule.needsExercise() && exercise == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This rule needs an exercise, e.g. Back squat.");
        }
        if (rule == BadgeRule.PROTEIN_DAYS_IN_WEEK && req.threshold() > 7) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A week has at most 7 days.");
        }
        d.setName(req.name().strip());
        d.setNameAr(blank(req.nameAr()));
        d.setDescription(blank(req.description()));
        d.setDescriptionAr(blank(req.descriptionAr()));
        d.setIcon(req.icon());
        d.setColor(req.color());
        d.setRule(rule);
        d.setThreshold(req.threshold());
        d.setExercise(rule.needsExercise() ? exercise : null);
        if (req.active() != null) d.setActive(req.active());
        if (req.sortOrder() != null) d.setSortOrder(req.sortOrder());
    }

    /** A slug from the name ("Squat King!" -> "squat_king"), made unique with a suffix. */
    private String uniqueCode(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isEmpty()) base = "badge";
        if (base.length() > 50) base = base.substring(0, 50);
        String code = base;
        for (int i = 2; definitions.existsByCode(code); i++) code = base + "_" + i;
        return code;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

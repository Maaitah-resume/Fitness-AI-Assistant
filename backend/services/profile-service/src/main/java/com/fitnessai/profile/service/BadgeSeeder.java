package com.fitnessai.profile.service;

import com.fitnessai.profile.model.BadgeDefinition;
import com.fitnessai.profile.model.BadgeRule;
import com.fitnessai.profile.repository.BadgeDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the starter badges the first time the service runs (an empty
 * badge table). After that they're ordinary admin-editable definitions.
 */
@Component
public class BadgeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BadgeSeeder.class);

    private record Starter(String code, String name, String nameAr, String description, String descriptionAr,
                           String icon, String color, BadgeRule rule, double threshold, String exercise) {
    }

    private static final Starter[] STARTERS = {
            new Starter("first_workout", "First rep", "أول تكرار", "Log your first workout", "سجّل أول تمرين لك",
                    "dumbbell", "blue", BadgeRule.WORKOUTS, 1, null),
            new Starter("workouts_10", "Ten sessions", "عشر جلسات", "Train on 10 different days", "تمرّن في 10 أيام مختلفة",
                    "calendar-check", "green", BadgeRule.WORKOUTS, 10, null),
            new Starter("workouts_25", "Regular", "منتظم", "Train on 25 different days", "تمرّن في 25 يومًا مختلفًا",
                    "medal", "yellow", BadgeRule.WORKOUTS, 25, null),
            new Starter("workouts_50", "Iron habit", "عادة حديدية", "Train on 50 different days", "تمرّن في 50 يومًا مختلفًا",
                    "award", "red", BadgeRule.WORKOUTS, 50, null),
            new Starter("streak_4", "On fire", "مشتعل", "Hit your weekly target 4 weeks in a row", "حقّق هدفك الأسبوعي 4 أسابيع متتالية",
                    "flame", "ember", BadgeRule.TRAINING_STREAK_WEEKS, 4, null),
            new Starter("prs_5", "Record breaker", "محطّم الأرقام", "Set 5 personal records", "حقّق 5 أرقام قياسية",
                    "trophy", "yellow", BadgeRule.PERSONAL_RECORDS, 5, null),
            new Starter("bodyweight_squat", "Bodyweight squat", "قرفصاء بوزن الجسم", "Squat your own body weight (estimated 1RM)",
                    "ارفع وزن جسمك في القرفصاء (حد أقصى مقدّر)", "weight", "blue", BadgeRule.LIFT_BODYWEIGHT_RATIO, 1, "Back squat"),
            new Starter("deadlift_100", "Triple digits", "ثلاث خانات", "Deadlift 100 kg (estimated 1RM)",
                    "ارفع 100 كغ في الرفعة الميتة (حد أقصى مقدّر)", "zap", "red", BadgeRule.LIFT_E1RM_KG, 100, "Deadlift"),
            new Starter("weighins_10", "On the scale", "على الميزان", "Log 10 weigh-ins", "سجّل وزنك 10 مرات",
                    "scale", "green", BadgeRule.WEIGH_INS, 10, null),
            new Starter("meals_50", "Fuel tracker", "متتبّع الوقود", "Log 50 meals", "سجّل 50 وجبة",
                    "utensils", "green", BadgeRule.MEALS_LOGGED, 50, null),
            new Starter("protein_5", "Protein on point", "بروتين مضبوط", "Hit your protein target on 5 days in a week",
                    "حقّق هدف البروتين في 5 أيام خلال أسبوع", "beef", "yellow", BadgeRule.PROTEIN_DAYS_IN_WEEK, 5, null),
    };

    private final BadgeDefinitionRepository definitions;

    public BadgeSeeder(BadgeDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (definitions.count() > 0) return;
        int order = 0;
        for (Starter s : STARTERS) {
            BadgeDefinition d = new BadgeDefinition(s.code());
            d.setName(s.name());
            d.setNameAr(s.nameAr());
            d.setDescription(s.description());
            d.setDescriptionAr(s.descriptionAr());
            d.setIcon(s.icon());
            d.setColor(s.color());
            d.setRule(s.rule());
            d.setThreshold(s.threshold());
            d.setExercise(s.exercise());
            d.setSortOrder(order += 10);
            definitions.save(d);
        }
        log.info("Seeded {} starter badges", STARTERS.length);
    }
}

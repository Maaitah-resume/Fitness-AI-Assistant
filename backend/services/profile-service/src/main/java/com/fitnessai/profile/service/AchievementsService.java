package com.fitnessai.profile.service;

import java.time.ZoneOffset;
import com.fitnessai.profile.repository.BadgeDefinitionRepository;
import com.fitnessai.profile.model.BadgeDefinition;
import com.fitnessai.profile.model.LiftEntry;
import com.fitnessai.profile.model.MealEntry;
import com.fitnessai.profile.model.Preferences;
import com.fitnessai.profile.model.Profile;
import com.fitnessai.profile.model.WeightEntry;
import com.fitnessai.profile.repository.LiftEntryRepository;
import com.fitnessai.profile.repository.MealEntryRepository;
import com.fitnessai.profile.repository.PreferencesRepository;
import com.fitnessai.profile.repository.ProfileRepository;
import com.fitnessai.profile.repository.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Streaks, personal records and badges, all derived from the existing logs (nothing extra is stored,
 * so they can never drift out of sync with what the user logged).
 */
@Service
public class AchievementsService {

    static final int DEFAULT_WEEKLY_TARGET = 2;
    static final int RECENT_PR_DAYS = 30;

    public record Streak(int current, int best, int target) {
    }

    public record PersonalRecord(String exercise, double weightKg, int reps, double estimatedOneRepMax, double previousBest,
                                 LocalDate date) {
    }

    /**
     * One badge for one user: its admin-defined look and rule, plus progress.
     * current/target show progress towards a badge that isn't earned yet; target 0 means the rule
     * can't be measured yet (a body-weight ratio before any weight is known).
     */
    public record Badge(String id, boolean earned, LocalDate earnedOn, double current, double target,
                        String name, String nameAr, String description, String descriptionAr,
                        String icon, String color, String rule, String exercise) {
    }

    /** A rule's outcome before the badge's display fields are attached. */
    record Progress(boolean earned, LocalDate earnedOn, double current, double target) {
        static Progress unmeasurable() {
            return new Progress(false, null, 0, 0);
        }
    }

    /** One user's logs, prepared once and shared by every badge rule. */
    record UserData(TreeSet<LocalDate> sessions, List<LiftEntry> lifts, List<PersonalRecord> prsOldestFirst,
                    List<WeightEntry> weights, List<MealEntry> mealsOldestFirst, int weeklyTarget, Double bodyWeight) {
    }

    public record Achievements(Streak trainingWeeks, Streak mealDays, int totalWorkouts, int totalPrs,
                               List<PersonalRecord> recentPrs, List<Badge> badges) {
    }

    private final LiftEntryRepository lifts;
    private final WeightEntryRepository weights;
    private final MealEntryRepository meals;
    private final PreferencesRepository preferences;
    private final ProfileRepository profiles;
    private final BadgeDefinitionRepository definitions;
    private final Clock clock;

    public AchievementsService(LiftEntryRepository lifts, WeightEntryRepository weights, MealEntryRepository meals,
                               PreferencesRepository preferences, ProfileRepository profiles,
                               BadgeDefinitionRepository definitions) {
        this.definitions = definitions;
        this.lifts = lifts;
        this.weights = weights;
        this.meals = meals;
        this.preferences = preferences;
        this.profiles = profiles;
        this.clock = Clock.systemUTC();
    }

    /**
     * Ids of lifts that beat every earlier entry of the same exercise (by estimated 1RM).
     * An exercise's first entry is its baseline, not a record. Expects entries oldest first.
     */
    public static Set<Long> personalRecordIds(List<LiftEntry> oldestFirst) {
        Map<String, Double> best = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (LiftEntry l : oldestFirst) {
            Double previous = best.get(l.getExerciseKey());
            double e1rm = l.estimatedOneRepMax();
            if (previous != null && e1rm > previous + 1e-9) ids.add(l.getId());
            if (previous == null || e1rm > previous) best.put(l.getExerciseKey(), e1rm);
        }
        return ids;
    }

    public Achievements forUser(long userId) {
        LocalDate today = LocalDate.now(clock);
        List<LiftEntry> l = lifts.findByUserIdOrderByDateAscIdAsc(userId);
        List<WeightEntry> w = weights.findByUserIdOrderByDateAsc(userId);
        List<MealEntry> m = meals.findByUserIdAndDateGreaterThanEqualOrderByDateDescIdDesc(userId, LocalDate.of(2000, 1, 1));
        Integer planned = preferences.findById(userId).map(Preferences::getTrainingDays).orElse(null);
        Profile profile = profiles.findById(userId).orElse(null);

        // workouts = distinct training days
        TreeSet<LocalDate> sessions = new TreeSet<>();
        l.forEach(x -> sessions.add(x.getDate()));

        // personal records, newest first
        Set<Long> prIds = personalRecordIds(l);
        List<PersonalRecord> prs = new ArrayList<>();
        Map<String, Double> bestSoFar = new HashMap<>();
        for (LiftEntry x : l) {
            double e1rm = x.estimatedOneRepMax();
            if (prIds.contains(x.getId())) {
                prs.add(new PersonalRecord(x.getExercise(), x.getWeightKg(), x.getReps(), round1(e1rm),
                        round1(bestSoFar.get(x.getExerciseKey())), x.getDate()));
            }
            bestSoFar.merge(x.getExerciseKey(), e1rm, Math::max);
        }
        prs.sort(Comparator.comparing(PersonalRecord::date).reversed());
        List<PersonalRecord> recent = prs.stream().filter(p -> !p.date().isBefore(today.minusDays(RECENT_PR_DAYS))).limit(6).toList();

        Streak training = trainingStreak(sessions, planned == null ? DEFAULT_WEEKLY_TARGET : planned, today);
        TreeSet<LocalDate> mealDates = new TreeSet<>();
        m.forEach(x -> mealDates.add(x.getDate()));
        Streak mealStreak = dayStreak(mealDates, today);

        // boxed on both branches: a mixed double/Double ternary unboxes and throws for a profile without a weight
        Double bodyWeight = !w.isEmpty() ? Double.valueOf(w.get(w.size() - 1).getWeightKg()) : profile == null ? null : profile.getWeightKg();
        List<MealEntry> mealsOldest = m.stream().sorted(Comparator.comparing(MealEntry::getDate).thenComparing(MealEntry::getId)).toList();
        UserData data = new UserData(sessions, l, prs.reversed(), w, mealsOldest,
                planned == null ? DEFAULT_WEEKLY_TARGET : planned, bodyWeight);

        // every active admin-defined badge, evaluated by its rule
        List<Badge> badges = definitions.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(d -> toBadge(d, evaluate(d, data)))
                .toList();
        return new Achievements(training, mealStreak, sessions.size(), prs.size(), recent, badges);
    }

    /** How many users have earned each badge (by code), for the admin portal. */
    public Map<String, Long> earnedCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (Profile p : profiles.findAll()) {
            forUser(p.getUserId()).badges().stream().filter(Badge::earned).forEach(b -> counts.merge(b.id(), 1L, Long::sum));
        }
        return counts;
    }

    static Badge toBadge(BadgeDefinition d, Progress p) {
        return new Badge(d.getCode(), p.earned(), p.earnedOn(), round1(p.current()), round1(p.target()),
                d.getName(), d.getNameAr(), d.getDescription(), d.getDescriptionAr(), d.getIcon(), d.getColor(),
                d.getRule().name().toLowerCase(java.util.Locale.ROOT), d.getExercise());
    }

    /** The rules engine: one case per BadgeRule, all driven by the badge's threshold (and exercise). */
    static Progress evaluate(BadgeDefinition d, UserData x) {
        double n = d.getThreshold();
        int count = (int) Math.max(1, Math.ceil(n));
        String exercise = d.getExercise() == null ? null : LiftEntry.keyOf(d.getExercise());
        return switch (d.getRule()) {
            case WORKOUTS -> countOf(List.copyOf(x.sessions()), count);
            case PERSONAL_RECORDS -> countOf(x.prsOldestFirst().stream().map(PersonalRecord::date).toList(), count);
            case WEIGH_INS -> countOf(x.weights().stream().map(WeightEntry::getDate).distinct().toList(), count);
            case MEALS_LOGGED -> countOf(x.mealsOldestFirst().stream().map(MealEntry::getDate).toList(), count);
            case TRAINING_STREAK_WEEKS -> {
                int best = trainingStreak(x.sessions(), x.weeklyTarget(), LocalDate.now(ZoneOffset.UTC)).best();
                yield best >= count ? new Progress(true, firstStreakOf(x.sessions(), x.weeklyTarget(), count), count, count)
                        : new Progress(false, null, best, count);
            }
            case MEAL_STREAK_DAYS -> dayRun(new TreeSet<>(x.mealsOldestFirst().stream().map(MealEntry::getDate).toList()), count);
            case PROTEIN_DAYS_IN_WEEK -> protein(x.mealsOldestFirst(), x.bodyWeight(), Math.min(count, 7));
            case WEIGHT_GAINED_KG -> weightChange(x.weights(), n, true);
            case WEIGHT_LOST_KG -> weightChange(x.weights(), n, false);
            case EXERCISE_SESSIONS -> countOf(x.lifts().stream().filter(l -> l.getExerciseKey().equals(exercise))
                    .map(LiftEntry::getDate).distinct().toList(), count);
            case LIFT_E1RM_KG -> lift(x.lifts(), exercise, n);
            case LIFT_BODYWEIGHT_RATIO -> x.bodyWeight() == null ? Progress.unmeasurable() : lift(x.lifts(), exercise, n * x.bodyWeight());
        };
    }

    /** Reaching `target` entries; earned on the date of the target-th entry. */
    private static Progress countOf(List<LocalDate> oldestFirst, int target) {
        boolean earned = oldestFirst.size() >= target;
        return new Progress(earned, earned ? oldestFirst.get(target - 1) : null, Math.min(oldestFirst.size(), target), target);
    }

    /** Estimated 1RM on an exercise reaching a threshold. */
    private static Progress lift(List<LiftEntry> oldestFirst, String exerciseKey, double threshold) {
        double best = 0;
        for (LiftEntry l : oldestFirst) {
            if (!l.getExerciseKey().equals(exerciseKey)) continue;
            best = Math.max(best, l.estimatedOneRepMax());
            if (best >= threshold) return new Progress(true, l.getDate(), threshold, threshold);
        }
        return new Progress(false, null, best, threshold);
    }

    /** `length` consecutive days with an entry; earned on the day the run first got that long. */
    private static Progress dayRun(TreeSet<LocalDate> days, int length) {
        int run = 0;
        int best = 0;
        LocalDate prev = null;
        for (LocalDate day : days) {
            run = prev != null && prev.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            if (run >= length) return new Progress(true, day, length, length);
            prev = day;
        }
        return new Progress(false, null, best, length);
    }

    /** `days` days within any 7 reaching 1.6 g protein per kg of body weight. */
    private static Progress protein(List<MealEntry> meals, Double bodyWeight, int days) {
        if (bodyWeight == null) return new Progress(false, null, 0, days);
        double target = bodyWeight * 1.6;
        Map<LocalDate, Double> perDay = new TreeMap<>();
        meals.forEach(x -> perDay.merge(x.getDate(), x.getProteinG(), Double::sum));
        List<LocalDate> hit = perDay.entrySet().stream().filter(e -> e.getValue() >= target).map(Map.Entry::getKey).toList();
        int bestInWindow = 0;
        for (int i = 0; i < hit.size(); i++) {
            int j = i;
            while (j + 1 < hit.size() && !hit.get(j + 1).isAfter(hit.get(i).plusDays(6))) j++;
            int inWindow = j - i + 1;
            if (inWindow >= days) return new Progress(true, hit.get(i + days - 1), days, days);
            bestInWindow = Math.max(bestInWindow, inWindow);
        }
        return new Progress(false, null, bestInWindow, days);
    }

    /** Gaining (or losing) `kg` since the first weigh-in; earned on the first weigh-in that got there. */
    private static Progress weightChange(List<WeightEntry> oldestFirst, double kg, boolean gain) {
        if (oldestFirst.isEmpty()) return new Progress(false, null, 0, kg);
        double start = oldestFirst.get(0).getWeightKg();
        double best = 0;
        for (WeightEntry e : oldestFirst) {
            double change = gain ? e.getWeightKg() - start : start - e.getWeightKg();
            best = Math.max(best, change);
            if (change >= kg - 1e-9) return new Progress(true, e.getDate(), kg, kg);
        }
        return new Progress(false, null, best, kg);
    }

    /** Consecutive Monday-Sunday weeks with at least `target` sessions; the current week counts once it's met. */
    static Streak trainingStreak(TreeSet<LocalDate> sessions, int target, LocalDate today) {
        Map<LocalDate, Integer> perWeek = new TreeMap<>();
        sessions.forEach(d -> perWeek.merge(d.with(DayOfWeek.MONDAY), 1, Integer::sum));
        LocalDate thisWeek = today.with(DayOfWeek.MONDAY);
        Predicate<LocalDate> met = wk -> perWeek.getOrDefault(wk, 0) >= target;

        int current = 0;
        LocalDate wk = met.test(thisWeek) ? thisWeek : thisWeek.minusWeeks(1); // an unfinished week doesn't break it
        while (met.test(wk)) {
            current++;
            wk = wk.minusWeeks(1);
        }
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate week : perWeek.keySet()) {
            if (!met.test(week)) { run = 0; prev = week; continue; }
            run = prev != null && prev.plusWeeks(1).equals(week) && run > 0 ? run + 1 : 1;
            best = Math.max(best, run);
            prev = week;
        }
        return new Streak(current, Math.max(best, current), target);
    }

    /** Consecutive days with an entry, ending today or yesterday (today isn't over yet). */
    static Streak dayStreak(TreeSet<LocalDate> days, LocalDate today) {
        int current = 0;
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        while (days.contains(d)) {
            current++;
            d = d.minusDays(1);
        }
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate day : days) {
            run = prev != null && prev.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = day;
        }
        return new Streak(current, best, 1);
    }

    /** The day a run of `length` qualifying weeks was first completed (end of that week). */
    private static LocalDate firstStreakOf(TreeSet<LocalDate> sessions, int target, int length) {
        Map<LocalDate, Integer> perWeek = new TreeMap<>();
        sessions.forEach(d -> perWeek.merge(d.with(DayOfWeek.MONDAY), 1, Integer::sum));
        int run = 0;
        LocalDate prev = null;
        for (Map.Entry<LocalDate, Integer> e : perWeek.entrySet()) {
            boolean ok = e.getValue() >= target;
            run = ok ? (prev != null && prev.plusWeeks(1).equals(e.getKey()) && run > 0 ? run + 1 : 1) : 0;
            prev = e.getKey();
            if (run >= length) {
                LocalDate wk = e.getKey();
                return sessions.subSet(wk, true, wk.plusDays(6), true).last();
            }
        }
        return null;
    }

    private static double round1(Double v) {
        return v == null ? 0 : Math.round(v * 10) / 10.0;
    }
}

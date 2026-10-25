package com.fitnessai.profile.service;

import com.fitnessai.profile.dto.ProgressDtos.LiftRequest;
import com.fitnessai.profile.dto.ProgressDtos.LiftView;
import com.fitnessai.profile.dto.ProgressDtos.ProgressView;
import com.fitnessai.profile.dto.ProgressDtos.WeightRequest;
import com.fitnessai.profile.dto.ProgressDtos.WeightView;
import com.fitnessai.profile.model.LiftEntry;
import com.fitnessai.profile.model.MealEntry;
import com.fitnessai.profile.model.Profile;
import com.fitnessai.profile.model.WeightEntry;
import com.fitnessai.profile.repository.LiftEntryRepository;
import com.fitnessai.profile.repository.MealEntryRepository;
import com.fitnessai.profile.repository.ProfileRepository;
import com.fitnessai.profile.repository.WeightEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Body-weight and lift log, plus the plain-text summary chat-service puts in the coach's prompt. */
@Service
public class ProgressService {

    /** Exercises listed in the coach summary, most recently trained first. */
    private static final int SUMMARY_EXERCISES = 8;

    private final WeightEntryRepository weights;
    private final LiftEntryRepository lifts;
    private final ProfileRepository profiles;
    private final MealEntryRepository meals;

    public ProgressService(WeightEntryRepository weights, LiftEntryRepository lifts, ProfileRepository profiles,
                           MealEntryRepository meals) {
        this.weights = weights;
        this.lifts = lifts;
        this.profiles = profiles;
        this.meals = meals;
    }

    public ProgressView view(long userId) {
        List<WeightEntry> w = weights.findByUserIdOrderByDateAsc(userId);
        List<LiftEntry> l = lifts.findByUserIdOrderByDateAscIdAsc(userId);
        Set<Long> prs = AchievementsService.personalRecordIds(l);
        return new ProgressView(w.stream().map(WeightView::from).toList(),
                l.stream().map(x -> LiftView.from(x, prs.contains(x.getId()))).toList(), summarize(w, l) + nutritionLine(userId));
    }

    public String summary(long userId) {
        return summarize(weights.findByUserIdOrderByDateAsc(userId), lifts.findByUserIdOrderByDateAscIdAsc(userId))
                + nutritionLine(userId);
    }

    /** Average intake over the days with logged meals in the last week, for the coach. */
    private String nutritionLine(long userId) {
        List<MealEntry> week = meals.findByUserIdAndDateGreaterThanEqualOrderByDateDescIdDesc(userId, today().minusDays(6));
        if (week.isEmpty()) return "";
        long days = week.stream().map(MealEntry::getDate).distinct().count();
        int kcal = week.stream().mapToInt(MealEntry::getCalories).sum();
        double protein = week.stream().mapToDouble(MealEntry::getProteinG).sum();
        return String.format(Locale.ROOT, "%nNutrition (last 7 days): %d meals on %d days, averaging %d kcal and %.0f g protein per logged day.",
                week.size(), days, Math.round((double) kcal / days), protein / days);
    }

    /** Same-day weigh-ins replace each other; the newest reading also becomes the profile weight (and BMI). */
    @Transactional
    public WeightView logWeight(long userId, WeightRequest req) {
        LocalDate date = req.date() == null ? today() : req.date();
        WeightEntry entry = weights.findByUserIdAndDate(userId, date).orElseGet(() -> new WeightEntry(userId, date, req.weightKg()));
        entry.setWeightKg(req.weightKg());
        WeightEntry saved = weights.save(entry);
        syncProfileWeight(userId);
        return WeightView.from(saved);
    }

    @Transactional
    public LiftView logLift(long userId, LiftRequest req) {
        LocalDate date = req.date() == null ? today() : req.date();
        LiftEntry entry = new LiftEntry(userId, date, req.exercise(), req.weightKg(), req.reps(), req.sets());
        // a record beats every earlier-or-same-day entry of the exercise; the first entry is just a baseline
        var previousBest = lifts.findByUserIdOrderByDateAscIdAsc(userId).stream()
                .filter(x -> x.getExerciseKey().equals(entry.getExerciseKey()) && !x.getDate().isAfter(date))
                .mapToDouble(LiftEntry::estimatedOneRepMax).max();
        boolean pr = previousBest.isPresent() && entry.estimatedOneRepMax() > previousBest.getAsDouble() + 1e-9;
        return LiftView.from(lifts.save(entry), pr);
    }

    @Transactional
    public void deleteWeight(long userId, long id) {
        WeightEntry e = weights.findById(id).filter(x -> x.getUserId() == userId).orElseThrow(ProgressService::notFound);
        weights.delete(e);
        syncProfileWeight(userId);
    }

    @Transactional
    public void deleteLift(long userId, long id) {
        lifts.delete(lifts.findById(id).filter(x -> x.getUserId() == userId).orElseThrow(ProgressService::notFound));
    }

    @Transactional
    public void deleteForUser(long userId) {
        weights.deleteByUserId(userId);
        lifts.deleteByUserId(userId);
        meals.deleteByUserId(userId);
    }

    private void syncProfileWeight(long userId) {
        weights.findFirstByUserIdOrderByDateDesc(userId).ifPresent(latest -> {
            Profile p = profiles.findById(userId).orElseGet(() -> new Profile(userId));
            p.setWeightKg(latest.getWeightKg());
            profiles.save(p);
        });
    }

    static String summarize(List<WeightEntry> w, List<LiftEntry> l) {
        if (w.isEmpty() && l.isEmpty()) return "(no training log yet)";
        StringBuilder sb = new StringBuilder();
        if (!w.isEmpty()) {
            WeightEntry first = w.get(0);
            WeightEntry last = w.get(w.size() - 1);
            sb.append("Body weight: ").append(kg(last.getWeightKg())).append(" on ").append(last.getDate());
            if (w.size() > 1) {
                sb.append(" (first logged ").append(kg(first.getWeightKg())).append(" on ").append(first.getDate())
                        .append(", change ").append(signed(last.getWeightKg() - first.getWeightKg())).append(")");
            }
            sb.append(". ").append(w.size()).append(" weigh-ins.\n");
        }
        if (!l.isEmpty()) {
            // group by exercise, keeping each group's entries oldest first
            Map<String, List<LiftEntry>> byExercise = new LinkedHashMap<>();
            l.forEach(e -> byExercise.computeIfAbsent(e.getExerciseKey(), k -> new ArrayList<>()).add(e));
            sb.append("Lifts (best set by estimated 1RM; first session -> latest session):\n");
            byExercise.values().stream()
                    .sorted(Comparator.comparing((List<LiftEntry> g) -> g.get(g.size() - 1).getDate()).reversed())
                    .limit(SUMMARY_EXERCISES)
                    .forEach(g -> sb.append(exerciseLine(g)).append('\n'));
            LocalDate lastLift = l.stream().map(LiftEntry::getDate).max(Comparator.naturalOrder()).orElseThrow();
            sb.append("Last logged workout: ").append(lastLift).append(".\n");
        }
        return sb.toString().strip();
    }

    private static String exerciseLine(List<LiftEntry> g) {
        LocalDate firstDay = g.get(0).getDate();
        LocalDate lastDay = g.get(g.size() - 1).getDate();
        LiftEntry firstBest = best(g, firstDay);
        LiftEntry lastBest = best(g, lastDay);
        long sessions = g.stream().map(LiftEntry::getDate).distinct().count();
        String line = "- " + lastBest.getExercise() + ": ";
        if (Objects.equals(firstDay, lastDay)) {
            return line + set(lastBest) + " on " + lastDay + " (e1RM " + kg(lastBest.estimatedOneRepMax()) + "), 1 session";
        }
        return line + set(firstBest) + " on " + firstDay + " -> " + set(lastBest) + " on " + lastDay
                + " (e1RM " + kg(firstBest.estimatedOneRepMax()) + " -> " + kg(lastBest.estimatedOneRepMax()) + "), "
                + sessions + " sessions";
    }

    private static LiftEntry best(List<LiftEntry> g, LocalDate day) {
        return g.stream().filter(e -> e.getDate().equals(day))
                .max(Comparator.comparingDouble(LiftEntry::estimatedOneRepMax)).orElseThrow();
    }

    private static String set(LiftEntry e) {
        return (e.getWeightKg() == 0 ? "bodyweight" : kg(e.getWeightKg())) + " x" + e.getReps()
                + (e.getSets() == null ? "" : " for " + e.getSets() + " sets");
    }

    private static String kg(double v) {
        return String.format(Locale.ROOT, "%.1f kg", v);
    }

    private static String signed(double v) {
        return String.format(Locale.ROOT, "%+.1f kg", v);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found.");
    }
}

package com.fitnessai.profile.service;

import com.fitnessai.profile.model.LiftEntry;
import com.fitnessai.profile.model.Profile;
import com.fitnessai.profile.model.ProfileChange;
import com.fitnessai.profile.model.WeightEntry;
import com.fitnessai.profile.repository.LiftEntryRepository;
import com.fitnessai.profile.repository.ProfileChangeRepository;
import com.fitnessai.profile.repository.ProfileRepository;
import com.fitnessai.profile.repository.WeightEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The profile's story so far: weight since the start and a timeline of
 * milestones (joining, first weigh-in and workout, goal/level changes,
 * badges earned), newest first.
 */
@Service
public class HistoryService {

    /** type: joined, first_weighin, first_workout, goal, level or badge; from/to carry the values. */
    public record Event(LocalDate date, String type, String from, String to) {
    }

    public record History(LocalDate since, Double startWeightKg, Double currentWeightKg, Double weightChangeKg,
                          int weighIns, int workouts, List<Event> events) {
    }

    private final ProfileRepository profiles;
    private final ProfileChangeRepository changes;
    private final WeightEntryRepository weights;
    private final LiftEntryRepository lifts;
    private final AchievementsService achievements;

    public HistoryService(ProfileRepository profiles, ProfileChangeRepository changes, WeightEntryRepository weights,
                          LiftEntryRepository lifts, AchievementsService achievements) {
        this.profiles = profiles;
        this.changes = changes;
        this.weights = weights;
        this.lifts = lifts;
        this.achievements = achievements;
    }

    /** Records goal and level changes; called by ProfileService before it applies an update. */
    void recordChanges(long userId, Profile before, String newGoal, String newLevel) {
        record(userId, "goal", before.getGoal(), newGoal);
        record(userId, "level", before.getFitnessLevel(), newLevel);
    }

    private void record(long userId, String field, Enum<?> old, String next) {
        if (next == null) return;
        String oldValue = old == null ? null : old.name().toLowerCase();
        String newValue = next.toLowerCase();
        if (!newValue.equals(oldValue)) {
            changes.save(new ProfileChange(userId, field, oldValue, newValue, java.time.Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public History forUser(long userId) {
        List<WeightEntry> w = weights.findByUserIdOrderByDateAsc(userId);
        List<LiftEntry> l = lifts.findByUserIdOrderByDateAscIdAsc(userId);
        List<ProfileChange> c = changes.findByUserIdOrderByChangedAtAsc(userId);
        Profile profile = profiles.findById(userId).orElse(null);

        List<Event> events = new ArrayList<>();
        if (!w.isEmpty()) events.add(new Event(w.get(0).getDate(), "first_weighin", null, String.valueOf(w.get(0).getWeightKg())));
        if (!l.isEmpty()) events.add(new Event(l.get(0).getDate(), "first_workout", null, l.get(0).getExercise()));
        for (ProfileChange ch : c) {
            events.add(new Event(ch.getChangedAt().atZone(ZoneOffset.UTC).toLocalDate(), ch.getField(), ch.getOldValue(), ch.getNewValue()));
        }
        achievements.forUser(userId).badges().stream()
                .filter(b -> b.earned() && b.earnedOn() != null)
                .forEach(b -> events.add(new Event(b.earnedOn(), "badge", b.id(), b.name()))); // from = code, to = name

        // Joined: when the profile was created, or (for profiles older than that column) the earliest activity.
        LocalDate since = profile != null && profile.getCreatedAt() != null
                ? profile.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()
                : events.stream().map(Event::date).min(Comparator.naturalOrder()).orElse(null);
        if (since != null) {
            LocalDate earliest = events.stream().map(Event::date).min(Comparator.naturalOrder()).orElse(since);
            since = since.isAfter(earliest) ? earliest : since;
            events.add(new Event(since, "joined", null, null));
        }
        // Events were added oldest-first; reversing before the (stable) sort keeps same-day, same-type
        // entries newest-first too, e.g. two goal changes made on one day.
        java.util.Collections.reverse(events);
        events.sort(Comparator.comparing(Event::date).thenComparing(e -> order(e.type())).reversed());

        Double start = w.isEmpty() ? null : w.get(0).getWeightKg();
        Double current = w.isEmpty() ? (profile == null ? null : profile.getWeightKg()) : w.get(w.size() - 1).getWeightKg();
        Double change = start == null || current == null ? null : Math.round((current - start) * 10) / 10.0;
        int workouts = (int) l.stream().map(LiftEntry::getDate).distinct().count();
        return new History(since, start, current, change, w.size(), workouts, events);
    }

    /** Same-day tie-break, so "joined" sorts before what followed it (the list is reversed). */
    private static int order(String type) {
        return Stream.of("joined", "first_weighin", "first_workout", "goal", "level", "badge").toList().indexOf(type);
    }

    @Transactional
    public void deleteForUser(long userId) {
        changes.deleteByUserId(userId);
    }
}

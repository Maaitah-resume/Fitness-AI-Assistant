package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.CoachPreferences;
import com.fitnessai.chat.dto.ChatDtos.DocSection;
import com.fitnessai.chat.dto.ChatDtos.GenerateRequest;
import com.fitnessai.chat.dto.ChatDtos.GeneratedFile;
import com.fitnessai.chat.dto.ChatDtos.LiftRow;
import com.fitnessai.chat.dto.ChatDtos.MealRow;
import com.fitnessai.chat.dto.ChatDtos.NutritionData;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.ProgressData;
import com.fitnessai.chat.dto.ChatDtos.WeightRow;
import com.fitnessai.chat.model.WeeklyReport;
import com.fitnessai.chat.repository.WeeklyReportRepository;
import com.fitnessai.chat.service.ReportWriter.ReportDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Weekly report for the last completed Monday-to-Sunday week. Generated on demand with the user's own
 * token (the frontend asks on the overview), at most once per week unless forced. The coach writes it
 * from the numbers; if Gemini is unavailable a data-only report is produced instead, so it never fails
 * for lack of AI quota.
 */
@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);
    static final double PROTEIN_G_PER_KG = 1.6;

    public record ReportView(String status, LocalDate weekStart, LocalDate weekEnd, String summary, String filename,
                             Long fileId, String downloadUrl, Boolean aiGenerated, Instant createdAt) {

        static ReportView of(WeeklyReport r) {
            return new ReportView("ready", r.getWeekStart(), r.getWeekStart().plusDays(6), r.getSummary(), r.getFilename(),
                    r.getFileId(), r.getDownloadUrl(), r.isAiGenerated(), r.getCreatedAt());
        }

        static ReportView noData(LocalDate start) {
            return new ReportView("no_data", start, start.plusDays(6), null, null, null, null, null, null);
        }
    }

    private final WeeklyReportRepository reports;
    private final DownstreamClients clients;
    private final ReportWriter writer;
    private final Clock clock;

    @Autowired
    public WeeklyReportService(WeeklyReportRepository reports, DownstreamClients clients, ReportWriter writer) {
        this(reports, clients, writer, Clock.systemUTC());
    }

    WeeklyReportService(WeeklyReportRepository reports, DownstreamClients clients, ReportWriter writer, Clock clock) {
        this.reports = reports;
        this.clients = clients;
        this.writer = writer;
        this.clock = clock;
    }

    /** Monday of the last fully finished week. */
    public LocalDate dueWeek() {
        LocalDate today = LocalDate.now(clock);
        return today.with(DayOfWeek.MONDAY).minusWeeks(1);
    }

    public Optional<ReportView> latest(long userId) {
        return reports.findFirstByUserIdOrderByWeekStartDesc(userId).map(ReportView::of);
    }

    public ReportView generate(long userId, String token, boolean force, boolean arabic) {
        LocalDate start = dueWeek();
        Optional<WeeklyReport> existing = reports.findByUserIdAndWeekStart(userId, start);
        if (existing.isPresent() && !force) return ReportView.of(existing.get());

        ProgressData progress = optional("progress lookup", () -> clients.myProgressData(token));
        NutritionData nutrition = optional("nutrition lookup", () -> clients.myNutrition(token));
        if (progress == null && nutrition == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Your logs couldn't be loaded right now. Try again shortly.");
        }
        ProfileSnapshot profile = optional("profile lookup", () -> clients.myProfile(token));
        CoachPreferences prefs = optional("preferences lookup", () -> clients.myPreferences(token));

        WeekFacts facts = WeekFacts.of(start, profile, prefs, progress, nutrition);
        if (!facts.hasData()) return ReportView.noData(start);

        ReportDraft draft;
        boolean ai = true;
        try {
            draft = writer.write(facts.digest(), arabic);
            if (draft == null || draft.sections() == null || draft.sections().isEmpty()) throw new IllegalStateException("empty draft");
        } catch (Exception e) {
            log.warn("Weekly report AI writing failed, using data-only report: {}",
                    NestedExceptionUtils.getMostSpecificCause(e).toString());
            draft = facts.fallback(arabic);
            ai = false;
        }

        String title = (arabic ? "التقرير الأسبوعي: " : "Weekly report: ") + facts.range(arabic);
        List<DocSection> sections = new ArrayList<>();
        if (draft.summary() != null && !draft.summary().isBlank()) {
            sections.add(new DocSection(arabic ? "الخلاصة" : "Summary", draft.summary()));
        }
        sections.addAll(draft.sections());
        if (!ai) {
            sections.add(new DocSection(null, arabic
                    ? "أُعدّ هذا التقرير من بياناتك مباشرة لأن المدرب الذكي لم يكن متاحًا."
                    : "This report was written from your numbers because the AI coach was unavailable."));
        }

        GeneratedFile file;
        try {
            file = clients.generateDocument(token, new GenerateRequest(title, sections));
        } catch (Exception e) {
            log.warn("Weekly report PDF failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The report PDF couldn't be created right now. Try again shortly.");
        }
        if (file == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The report PDF couldn't be created right now.");

        WeeklyReport report = existing.orElseGet(() -> new WeeklyReport(userId, start));
        report.fill(file.id(), file.filename(), file.downloadUrl(), draft.summary(), ai);
        try {
            return ReportView.of(reports.saveAndFlush(report));
        } catch (DataIntegrityViolationException race) {
            // a parallel request generated this week's report first; use that one
            return reports.findByUserIdAndWeekStart(userId, start).map(ReportView::of).orElseThrow(() -> race);
        }
    }

    private static <T> T optional(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("{} failed for weekly report: {}", what, e.getMessage());
            return null;
        }
    }

    /** The week's numbers, as text for the model and as a fallback report. */
    record WeekFacts(LocalDate start, LocalDate end, String goal, Integer plannedDays,
                     Double weightStart, Double weightEnd, int weighIns,
                     int sessions, Map<String, double[]> lifts, // exercise -> {best this week, best before (or NaN)}
                     int mealDays, int meals, double avgKcal, double avgProtein, Double proteinTarget) {

        static WeekFacts of(LocalDate start, ProfileSnapshot profile, CoachPreferences prefs, ProgressData progress, NutritionData nutrition) {
            LocalDate end = start.plusDays(6);
            List<WeightRow> allWeights = progress == null || progress.weights() == null ? List.of() : progress.weights();
            List<LiftRow> allLifts = progress == null || progress.lifts() == null ? List.of() : progress.lifts();
            List<MealRow> allMeals = nutrition == null || nutrition.meals() == null ? List.of() : nutrition.meals();

            List<WeightRow> weekWeights = allWeights.stream().filter(w -> in(w.date(), start, end))
                    .sorted(Comparator.comparing(WeightRow::date)).toList();
            Double wStart = null;
            Double wEnd = weekWeights.isEmpty() ? null : weekWeights.get(weekWeights.size() - 1).weightKg();
            if (!weekWeights.isEmpty()) {
                // compare with the last reading before the week when there is one
                wStart = allWeights.stream().filter(w -> LocalDate.parse(w.date()).isBefore(start))
                        .max(Comparator.comparing(WeightRow::date)).map(WeightRow::weightKg)
                        .orElse(weekWeights.get(0).weightKg());
            }

            List<LiftRow> weekLifts = allLifts.stream().filter(l -> in(l.date(), start, end)).toList();
            Map<String, double[]> lifts = new LinkedHashMap<>();
            for (LiftRow l : weekLifts) {
                String name = l.exercise().strip();
                double before = allLifts.stream().filter(x -> x.exercise().strip().equalsIgnoreCase(name)
                                && LocalDate.parse(x.date()).isBefore(start))
                        .mapToDouble(LiftRow::estimatedOneRepMax).max().orElse(Double.NaN);
                lifts.merge(name, new double[]{l.estimatedOneRepMax(), before},
                        (a, b) -> new double[]{Math.max(a[0], b[0]), a[1]});
            }

            List<MealRow> weekMeals = allMeals.stream().filter(m -> in(m.date(), start, end)).toList();
            long mealDays = weekMeals.stream().map(MealRow::date).distinct().count();
            double kcal = weekMeals.stream().mapToInt(MealRow::calories).sum();
            double protein = weekMeals.stream().mapToDouble(MealRow::proteinG).sum();
            Double bodyWeight = wEnd != null ? wEnd : profile == null ? null : profile.weightKg();

            return new WeekFacts(start, end, profile == null ? null : profile.goal(), prefs == null ? null : prefs.trainingDays(),
                    wStart, wEnd, weekWeights.size(),
                    (int) weekLifts.stream().map(LiftRow::date).distinct().count(), lifts,
                    (int) mealDays, weekMeals.size(), mealDays == 0 ? 0 : kcal / mealDays, mealDays == 0 ? 0 : protein / mealDays,
                    bodyWeight == null ? null : Math.round(bodyWeight * PROTEIN_G_PER_KG * 1.0) * 1.0);
        }

        private static boolean in(String date, LocalDate start, LocalDate end) {
            LocalDate d = LocalDate.parse(date);
            return !d.isBefore(start) && !d.isAfter(end);
        }

        boolean hasData() {
            return weighIns > 0 || sessions > 0 || meals > 0;
        }

        String range(boolean arabic) {
            Locale locale = arabic ? Locale.forLanguageTag("ar") : Locale.ENGLISH;
            DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM", locale);
            return day.format(start) + " – " + day.format(end) + " " + end.getYear();
        }

        String digest() {
            StringBuilder sb = new StringBuilder();
            sb.append("Week: ").append(start).append(" to ").append(end).append('\n');
            sb.append("Goal: ").append(goal == null ? "not set" : goal.replace('_', ' ')).append('\n');
            sb.append("Training sessions: ").append(sessions)
                    .append(plannedDays == null ? " (no planned days set)" : " of " + plannedDays + " planned").append('\n');
            if (lifts.isEmpty()) sb.append("Lifts: none logged\n");
            lifts.forEach((name, v) -> sb.append(String.format(Locale.ROOT, "Lift %s: best estimated 1RM %.1f kg this week%s%n",
                    name, v[0], Double.isNaN(v[1]) ? " (first time logged)"
                            : String.format(Locale.ROOT, ", previous best %.1f kg (%+.1f kg)", v[1], v[0] - v[1]))));
            if (weighIns == 0) sb.append("Body weight: no weigh-ins this week\n");
            else sb.append(String.format(Locale.ROOT, "Body weight: %.1f kg -> %.1f kg (%+.1f kg), %d weigh-ins%n",
                    weightStart, weightEnd, weightEnd - weightStart, weighIns));
            if (meals == 0) sb.append("Nutrition: no meals logged\n");
            else sb.append(String.format(Locale.ROOT, "Nutrition: %d meals on %d days, averaging %.0f kcal and %.0f g protein per logged day%s%n",
                    meals, mealDays, avgKcal, avgProtein, proteinTarget == null ? "" : String.format(Locale.ROOT, "; protein target %.0f g/day", proteinTarget)));
            return sb.toString();
        }

        /** A plain, numbers-only report used when the AI is unavailable. */
        ReportDraft fallback(boolean ar) {
            List<DocSection> sections = new ArrayList<>();
            StringBuilder training = new StringBuilder();
            training.append(ar ? "- الجلسات: " : "- Sessions: ").append(sessions)
                    .append(plannedDays == null ? "" : (ar ? " من " : " of ") + plannedDays + (ar ? " مخطط لها" : " planned")).append('\n');
            lifts.forEach((name, v) -> training.append(String.format(Locale.ROOT, "- %s: %.1f kg%s%n", name, v[0],
                    Double.isNaN(v[1]) ? (ar ? " (أول تسجيل)" : " (first time logged)") : String.format(Locale.ROOT, " (%+.1f kg)", v[0] - v[1]))));
            if (sessions == 0) training.append(ar ? "- لم تُسجَّل رفعات هذا الأسبوع.\n" : "- No lifts logged this week.\n");
            sections.add(new DocSection(ar ? "التمرين" : "Training", training.toString().strip()));

            sections.add(new DocSection(ar ? "وزن الجسم" : "Body weight", weighIns == 0
                    ? (ar ? "- لا توجد أوزان مسجلة هذا الأسبوع." : "- No weigh-ins this week.")
                    : String.format(Locale.ROOT, ar ? "- %.1f كغ ← %.1f كغ (%+.1f كغ)" : "- %.1f kg to %.1f kg (%+.1f kg)", weightStart, weightEnd, weightEnd - weightStart)));

            sections.add(new DocSection(ar ? "التغذية" : "Nutrition", meals == 0
                    ? (ar ? "- لم تُسجَّل وجبات هذا الأسبوع." : "- No meals logged this week.")
                    : String.format(Locale.ROOT, ar ? "- متوسط %.0f سعرة و%.0f غ بروتين يوميًا (%d أيام مسجلة)%s" : "- Averaging %.0f kcal and %.0f g protein a day over %d logged days%s",
                    avgKcal, avgProtein, mealDays, proteinTarget == null ? "" : String.format(Locale.ROOT, ar ? "؛ الهدف %.0f غ" : "; target %.0f g", proteinTarget))));

            String summary = String.format(Locale.ROOT, ar ? "%d جلسات تدريب هذا الأسبوع." : "%d training sessions this week.", sessions);
            return new ReportDraft(summary, sections);
        }
    }
}

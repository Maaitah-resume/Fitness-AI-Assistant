package com.fitnessai.profile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How the user wants GymAI to behave: display units, coach style and training constraints. */
@Entity
@Table(name = "preferences")
public class Preferences {

    public enum Units { METRIC, IMPERIAL }

    public enum AnswerLength { SHORT, DETAILED }

    public enum Tone { FRIENDLY, STRICT }

    public enum Equipment { FULL_GYM, DUMBBELLS, HOME, BODYWEIGHT }

    // Logical reference to user-service's users.id — no cross-service FK.
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Units units = Units.METRIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_length", nullable = false)
    private AnswerLength answerLength = AnswerLength.SHORT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tone tone = Tone.FRIENDLY;

    @Column(name = "training_days")
    private Integer trainingDays;

    @Enumerated(EnumType.STRING)
    private Equipment equipment;

    @Column(length = 300)
    private String injuries;

    @Column(length = 300)
    private String allergies;

    /** Comma-separated diet codes, e.g. "halal,lactose_free" (see PreferencesDtos.DIETS). */
    @Column(length = 120)
    private String diet;

    protected Preferences() {
    }

    public Preferences(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
    public Units getUnits() { return units; }
    public AnswerLength getAnswerLength() { return answerLength; }
    public Tone getTone() { return tone; }
    public Integer getTrainingDays() { return trainingDays; }
    public Equipment getEquipment() { return equipment; }
    public String getInjuries() { return injuries; }
    public String getAllergies() { return allergies; }
    public String getDiet() { return diet; }

    public void setUnits(Units units) { this.units = units; }
    public void setAnswerLength(AnswerLength answerLength) { this.answerLength = answerLength; }
    public void setTone(Tone tone) { this.tone = tone; }
    public void setTrainingDays(Integer trainingDays) { this.trainingDays = trainingDays; }
    public void setEquipment(Equipment equipment) { this.equipment = equipment; }
    public void setInjuries(String injuries) { this.injuries = injuries; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public void setDiet(String diet) { this.diet = diet; }
}

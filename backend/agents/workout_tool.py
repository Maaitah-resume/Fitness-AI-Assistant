def suggest_workout(goal: str, experience: str):
    """
    Provide workout suggestions based on user goal and level.
    """
    plans = {
        "weight loss": {
            "beginner": "20 min walking + 10 min bodyweight exercises",
            "intermediate": "30 min HIIT + light strength training",
            "advanced": "45 min HIIT + 30 min strength training"
        },
        "muscle gain": {
            "beginner": "Full body 3x/week with light weights",
            "intermediate": "Push/Pull/Leg split 4–5 days",
            "advanced": "PPL + progressive overload tracking"
        },
        "general fitness": {
            "beginner": "Walking + stretching 20 min/day",
            "intermediate": "Jogging + bodyweight circuit",
            "advanced": "Run + mixed strength/cardio circuits"
        }
    }

    if goal not in plans:
        return "Unknown goal. Try weight loss, muscle gain, or general fitness."

    return plans[goal].get(experience, "Unknown experience level.")


def workout_duration_calculator(sets, reps, rest_sec):
    """
    Estimate total workout duration in minutes.
    """
    total_reps_time = sets * reps * 3  # assume average 3 seconds per rep
    total_rest_time = (sets - 1) * rest_sec

    return round((total_reps_time + total_rest_time) / 60, 2)

def suggest_workout(goal: str, experience: str):
    """
    Provide workout suggestions based on user goal and level.
    """
    plans = {
        "weight loss": {
            "beginner": (
                "• 20 min brisk walking\n"
                "• 10 min bodyweight circuit (squats, lunges, push-ups)\n"
                "• 5 min stretching\n"
            ),
            "intermediate": (
                "• 25 min HIIT (40 sec work / 20 sec rest)\n"
                "• 15 min strength (dumbbells + core)\n"
                "• Optional: 10 min incline treadmill\n"
            ),
            "advanced": (
                "• 45 min HIIT with sprints\n"
                "• 30 min strength training (upper/lower split)\n"
                "• Optional: 15 min steady-state cardio\n"
            ),
        },

        "muscle gain": {
            "beginner": (
                "• Full-body workout 3x/week\n"
                "  - Squat, bench press, row (light)\n"
                "  - Dumbbell curls + tricep dips\n"
                "• 5–10 min warm-up + stretching\n"
            ),
            "intermediate": (
                "• Push/Pull/Leg split 4–5 days/week\n"
                "• Progressive overload each week\n"
                "Push: chest, shoulders, triceps\n"
                "Pull: back, biceps\n"
                "Legs: quads, hamstrings, calves\n"
            ),
            "advanced": (
                "• PPL with accessory work + strength periodization\n"
                "• Track volume (sets × reps × weight)\n"
                "• Add RPE-based training + supersets\n"
            ),
        },

        "general fitness": {
            "beginner": (
                "• 20 min walking\n"
                "• 10 min mobility + stretching\n"
                "• Light bodyweight exercises (plank, glute bridges)\n"
            ),
            "intermediate": (
                "• Jogging 15–20 min\n"
                "• Full-body circuit (push-ups, squats, rows)\n"
                "• Light core work (planks + leg raises)\n"
            ),
            "advanced": (
                "• 5 km run or 25 min cardio\n"
                "• Strength + mobility hybrid session\n"
                "• Mixed cardio/strength intervals (EMOM or AMRAP)\n"
            ),
        },

        "strength training": {
            "beginner": (
                "• 3-day beginner strength routine:\n"
                "  - Squat, bench, row\n"
                "  - Deadlift (light), shoulder press\n"
                "  - Accessory: curls, triceps\n"
            ),
            "intermediate": (
                "• 4-day Upper/Lower split\n"
                "Upper: bench, OHP, rows, pulldowns\n"
                "Lower: squat, RDL, lunges, calves\n"
            ),
            "advanced": (
                "• Powerlifting-style program:\n"
                "  - Squat, Bench, Deadlift 2× weekly\n"
                "  - Heavy/light system\n"
                "  - RPE-based progression\n"
            ),
        },

        "endurance": {
            "beginner": (
                "• 15 min jog + 10 min walk\n"
                "• Light mobility work\n"
            ),
            "intermediate": (
                "• 30–40 min steady-state run\n"
                "• 10 min intervals (fast/slow)\n"
            ),
            "advanced": (
                "• 60–75 min run\n"
                "• Tempo training + uphill repeats\n"
                "• Stretching & recovery session\n"
            ),
        },

        "home workout": {
            "beginner": (
                "• 10 min warm-up\n"
                "• 3 rounds: squats, push-ups, lunges, plank\n"
                "• 5 min cool-down\n"
            ),
            "intermediate": (
                "• 20–25 min HIIT\n"
                "• 4 circuits: burpees, mountain climbers, jump squats, dips\n"
                "• Core finisher: leg raises + plank\n"
            ),
            "advanced": (
                "• 30–40 min advanced HIIT\n"
                "• Plyometrics + core complexes\n"
                "• Optional: resistance bands routine\n"
            ),
        },
    
    "crossfit": {
    "beginner": (
        "• 10 min mobility\n"
        "• 3 rounds: 10 air squats, 10 push-ups, 200m row\n"
        "• 5 min cooldown"
    ),
    "intermediate": (
        "• WOD: 15 min AMRAP\n"
        "   - 5 pull-ups\n"
        "   - 10 wall balls\n"
        "   - 15 box jumps\n"
    ),
    "advanced": (
        "• Hero WOD: Murph (scaled)\n"
        "• Or heavy EMOM training\n"
    ),
},

"bodybuilding": {
    "beginner": (
        "• Full body split 3x/week\n"
        "• Machines + dumbbells\n"
    ),
    "intermediate": (
        "• 5-day bro split\n"
        "Chest / Back / Shoulders / Legs / Arms"
    ),
    "advanced": (
        "• Push/Pull/Legs + accessory\n"
        "• High volume hypertrophy"
    ),
},

"flexibility": {
    "beginner": "• 10 min full body stretch\n• Hip mobility\n• Shoulder mobility",
    "intermediate": "• 20 min yoga\n• Deep flexibility holds\n• Breath control",
    "advanced": "• 30–45 min yoga flow\n• Splits mobility progression",
},

"rehab_friendly": {
    "beginner": "• Light band exercises\n• Chair squats\n• Slow walking",
    "intermediate": "• Low-impact circuit\n• Elliptical\n• Light core work",
    "advanced": "• Controlled strength session\n• Mobility flow\n• Balance training",
},

    
    }

    goal = goal.lower().strip()
    experience = experience.lower().strip()

    if goal not in plans:
        return (
            "Unknown goal. Try one of these:\n"
            "- weight loss\n"
            "- muscle gain\n"
            "- general fitness\n"
            "- strength training\n"
            "- endurance\n"
            "- home workout"
        )

    return plans[goal].get(experience, "Unknown experience level.")


def workout_duration_calculator(sets, reps, rest_sec):
    """
    Estimate total workout duration in minutes.
    """
    total_reps_time = sets * reps * 3  # assume average 3 seconds per rep
    total_rest_time = (sets - 1) * rest_sec

    return round((total_reps_time + total_rest_time) / 60, 2)


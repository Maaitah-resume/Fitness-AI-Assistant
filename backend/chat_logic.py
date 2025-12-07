import google.generativeai as genai
import os
import re
from typing import List

from .config import GOOGLE_GEMINI_API_KEY, GEMINI_MODEL_NAME
from .agents.fitness_tools import (
    calculate_heart_rate_zones,
    calculate_protein_needs,
    calculate_water_intake,
)
from .agents.bmi_tools import calculate_bmi
from .agents.calorie_tools import calculate_daily_calories, CALORIE_TABLE
from .agents.workout_tool import suggest_workout, workout_duration_calculator
from .chat_models import ChatMessage


# -------------------------------------------------------------------------
# SYSTEM PROMPT
# -------------------------------------------------------------------------
def load_system_prompt() -> str:
    """
    Reads the system prompt from prompts/system_prompt.txt.
    """
    prompt_path = os.path.join(
        os.path.dirname(__file__), "prompts", "system_prompt.txt"
    )
    with open(prompt_path, "r", encoding="utf-8") as f:
        return f.read()


SYSTEM_PROMPT = load_system_prompt()


# -------------------------------------------------------------------------
# INITIALIZE GEMINI CLIENT
# -------------------------------------------------------------------------
def get_client():
    if not GOOGLE_GEMINI_API_KEY:
        raise ValueError("GOOGLE_GEMINI_API_KEY is not set.")

    genai.configure(api_key=GOOGLE_GEMINI_API_KEY)
    return genai.GenerativeModel(GEMINI_MODEL_NAME)


# -------------------------------------------------------------------------
# PATTERN MATCH HELPERS
# -------------------------------------------------------------------------
def match_bmi_command(msg):
    match = re.match(r"bmi\s+(\d+)\s+(\d+)", msg.lower())
    return match.groups() if match else None


def match_calorie_command(msg):
    match = re.match(
        r"calories\s+(\d+)\s+(\d+)\s+(\d+)\s+(male|female)\s+(sedentary|light|medium|active)",
        msg.lower(),
    )
    return match.groups() if match else None


def match_calorie_lookup(msg):
    match = re.match(r"calorie\s+lookup\s+(.+)", msg.lower())
    return match.group(1).lower() if match else None


def match_workout_suggestion(msg):
    match = re.match(
        r"workout\s+(weight loss|muscle gain|general fitness|strength training|endurance|home workout)\s+(beginner|intermediate|advanced)",
        msg.lower(),
    )
    return match.groups() if match else None


def match_workout_duration(msg):
    match = re.match(r"workout duration\s+(\d+)\s+(\d+)\s+(\d+)", msg.lower())
    return match.groups() if match else None


def match_heart_rate(msg):
    match = re.match(r"heart rate\s+(\d+)", msg.lower())
    return int(match.group(1)) if match else None


def match_protein(msg):
    match = re.match(
        r"protein\s+(\d+)\s+(sedentary|moderate|active|very_active|athlete|bodybuilder|cutting_phase)",
        msg.lower(),
    )
    return match.groups() if match else None


def match_hydration(msg):
    match = re.match(
        r"hydration\s+(\d+)\s+(sedentary|moderate|active|very_active|athlete|hot_weather|intense_training)",
        msg.lower(),
    )
    return match.groups() if match else None


# -------------------------------------------------------------------------
# MAIN HANDLER
# -------------------------------------------------------------------------
def generate_response(user_message: str, history: List[ChatMessage] = None) -> str:
    message = user_message.strip()

    try:
        # --------------------- BMI ---------------------
        bmi_args = match_bmi_command(message)
        if bmi_args:
            kg, cm = map(int, bmi_args)
            return calculate_bmi(kg, cm)

        # ---------------- DAILY CALORIES ----------------
        cal_args = match_calorie_command(message)
        if cal_args:
            weight, height, age, gender, activity = cal_args
            return calculate_daily_calories(
                int(weight), int(height), int(age), gender, activity
            )

        # ---------------- FOOD CALORIE LOOKUP -----------
        food_name = match_calorie_lookup(message)
        if food_name:
            calories = CALORIE_TABLE.get(food_name)
            if calories is None:
                return f"Sorry, I don’t have calorie info for '{food_name}'."
            return f"{food_name.capitalize()} has {calories} calories."

        # ---------------- WORKOUT PLAN ------------------
        workout_args = match_workout_suggestion(message)
        if workout_args:
            goal, level = workout_args
            plan = suggest_workout(goal, level)
            return (
                f"### Workout Plan for {goal.title()} ({level.title()})\n\n"
                f"{plan}\n\n"
                "Remember to warm up, hydrate, and start gradually!"
            )

        # ------------ WORKOUT DURATION ------------------
        duration_args = match_workout_duration(message)
        if duration_args:
            sets, reps, rest = map(int, duration_args)
            total_min = workout_duration_calculator(sets, reps, rest)
            return f"Estimated workout duration: **{total_min} minutes**."

        # ---------------- HEART RATE --------------------
        heart_rate = match_heart_rate(message)
        if heart_rate:
            return analyze_heart_rate_zones(heart_rate)

        # ---------------- PROTEIN NEED ------------------
        protein_args = match_protein(message)
        if protein_args:
            weight, activity = protein_args
            grams = calculate_protein_intake(int(weight), activity)
            return f"You should aim for **{grams}g of protein per day**."

        # ---------------- HYDRATION ---------------------
        hyd_args = match_hydration(message)
        if hyd_args:
            weight, activity = hyd_args
            ml = calculate_daily_water_intake(int(weight), activity)
            return f"Recommended daily water intake: **{ml} ml**."

        # -----------------------------------------------------------------
        # FALLBACK → USE GEMINI WITH SYSTEM PROMPT + OPTIONAL HISTORY
        # -----------------------------------------------------------------
        model = get_client()

        conversation = [
            {"role": "system", "parts": SYSTEM_PROMPT},
        ]

        if history:
            for m in history:
                conversation.append({"role": m.role, "parts": m.content})

        mapped_role = "user" if m.role == "assistant" else "model"
        conversation.append({"role": mapped_role, "parts": m.content})


        response = model.generate_content(conversation)
        return response.text.strip()

    except Exception as e:
        print("🔥 Backend error:", e)
        return "Sorry, I couldn’t process your request. Please try again."

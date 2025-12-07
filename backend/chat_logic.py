import google.generativeai as genai
import os
import re
from typing import List

from .config import GOOGLE_GEMINI_API_KEY, GEMINI_MODEL_NAME
from .utils.logger import log_conv as log_conversation
from .agents.bmi_tools import calculate_bmi
from .agents.calorie_tools import calculate_daily_calories, estimate_meal_calories
from .agents.workout_tool import suggest_workout, workout_duration_calculator
from .agents.fitness_tools import (
    calculate_body_fat, calculate_ideal_weight, calculate_protein_needs,
    calculate_water_intake, calculate_heart_rate_zones, calculate_macros
)
from .chat_models import ChatMessage


# Configure Gemini client
_client = None

def get_client():
    """Get or create Gemini client."""
    global _client
    if _client is None:
        if not GOOGLE_GEMINI_API_KEY:
            raise ValueError("GOOGLE_GEMINI_API_KEY is not set. Please check your .env file.")
        genai.configure(api_key=GOOGLE_GEMINI_API_KEY)
        _client = genai.GenerativeModel(GEMINI_MODEL_NAME)
    return _client


def load_system_prompt() -> str:
    """
    Read the system prompt from the prompts/system_prompt.txt file.
    """
    prompt_path = os.path.join(os.path.dirname(__file__), "prompts", "system_prompt.txt")
    with open(prompt_path, "r", encoding="utf-8") as f:
        return f.read()


SYSTEM_PROMPT = load_system_prompt()


def try_handle_bmi_command(user_message: str) -> str | None:
    """
    Handle BMI calculation command.
    Format: bmi <weight> <height>
    Example: bmi 70 175
    """
    text = user_message.lower().strip()
    match = re.match(r"^bmi\s+(\d+(\.\d+)?)\s+(\d+(\.\d+)?)$", text)
    if not match:
        return None

    weight = float(match.group(1))
    height = float(match.group(3))

    result = calculate_bmi(weight_kg=weight, height_cm=height)

    if result["bmi_value"] is None:
        return "Your height value is invalid. Please check and try again."

    bmi_value = result["bmi_value"]
    category = result["category"]

    return (
        f"Your BMI is {bmi_value}, which is in the '{category}' range. "
        "This is only a general guideline. For personal health advice, "
        "please consult a doctor or nutrition specialist."
    )


def try_handle_daily_calories_command(user_message: str) -> str | None:
    """
    Handle daily calories calculation command.
    Format: calories <weight> <height> <age> <gender> <activity_level>
    Example: calories 70 175 30 male medium
    """
    text = user_message.lower().strip()
    
    # Pattern: calories <weight> <height> <age> <gender> <activity>
    match = re.match(r"^calories?\s+(\d+(\.\d+)?)\s+(\d+(\.\d+)?)\s+(\d+)\s+(male|female)\s+(low|medium|high)$", text)
    if not match:
        return None

    weight = float(match.group(1))
    height = float(match.group(3))
    age = int(match.group(5))
    gender = match.group(6)
    activity = match.group(7)

    try:
        calories = calculate_daily_calories(weight, height, age, gender, activity)
        return (
            f"Your estimated daily calorie needs: **{calories} calories**\n\n"
            f"Based on:\n"
            f"- Weight: {weight} kg\n"
            f"- Height: {height} cm\n"
            f"- Age: {age} years\n"
            f"- Gender: {gender}\n"
            f"- Activity level: {activity}\n\n"
            "This uses the Mifflin-St Jeor equation. For personalized nutrition advice, consult a registered dietitian."
        )
    except Exception as e:
        return f"Error calculating calories: {str(e)}"


def try_handle_meal_calories_command(user_message: str) -> str | None:
    """
    Handle meal calories estimation command.
    Format: meal calories <item1> <item2> ...
    Example: meal calories apple chicken breast rice
    """
    text = user_message.lower().strip()
    
    # Pattern: meal calories <items...>
    match = re.match(r"^meal\s+calories?\s+(.+)$", text)
    if not match:
        return None

    items_str = match.group(1)
    items = [item.strip() for item in items_str.split()]

    try:
        total_calories = estimate_meal_calories(items)
        found_items = [item for item in items if item.lower() in ["apple", "banana", "chicken breast", "rice", "salad"]]
        unknown_items = [item for item in items if item.lower() not in ["apple", "banana", "chicken breast", "rice", "salad"]]
        
        result = f"Estimated meal calories: **{total_calories} calories**\n\n"
        result += f"Items: {', '.join(items)}\n\n"
        
        if unknown_items:
            result += f"Note: Some items ({', '.join(unknown_items)}) are not in the database and were counted as 0 calories.\n"
            result += "Available items: apple, banana, chicken breast, rice, salad\n\n"
        
        result += "This is an estimate. Actual calories may vary based on portion sizes and preparation methods."
        return result
    except Exception as e:
        return f"Error calculating meal calories: {str(e)}"


def try_handle_workout_plan_command(user_message: str) -> str | None:
    """
    Handle workout plan suggestion command.
    Format: workout <goal> <experience>
    Example: workout weight loss beginner
    """
    text = user_message.lower().strip()
    
    # Pattern: workout <goal> <experience>
    match = re.match(r"^workout\s+(weight\s+loss|muscle\s+gain|general\s+fitness)\s+(beginner|intermediate|advanced)$", text)
    if not match:
        return None

    goal = match.group(1).replace(" ", "_") if " " in match.group(1) else match.group(1)
    experience = match.group(2)

    # Normalize goal
    goal_map = {
        "weight_loss": "weight loss",
        "muscle_gain": "muscle gain",
        "general_fitness": "general fitness"
    }
    goal = goal_map.get(goal, goal)

    try:
        plan = suggest_workout(goal, experience)
        return (
            f"**Workout Plan for {goal.title()} ({experience.title()})**\n\n"
            f"{plan}\n\n"
            "Remember to warm up before exercising and cool down afterward. "
            "Start gradually and increase intensity over time. If you experience pain, stop and consult a healthcare professional."
        )
    except Exception as e:
        return f"Error generating workout plan: {str(e)}"


def try_handle_workout_duration_command(user_message: str) -> str | None:
    """
    Handle workout duration calculation command.
    Format: duration <sets> <reps> <rest_seconds>
    Example: duration 3 10 60
    """
    text = user_message.lower().strip()
    
    # Pattern: duration <sets> <reps> <rest>
    match = re.match(r"^(workout\s+)?duration\s+(\d+)\s+(\d+)\s+(\d+)$", text)
    if not match:
        return None

    sets = int(match.group(2))
    reps = int(match.group(3))
    rest_sec = int(match.group(4))

    try:
        duration_min = workout_duration_calculator(sets, reps, rest_sec)
        return (
            f"**Estimated Workout Duration: {duration_min} minutes**\n\n"
            f"Based on:\n"
            f"- Sets: {sets}\n"
            f"- Reps per set: {reps}\n"
            f"- Rest between sets: {rest_sec} seconds\n\n"
            "This is an estimate. Actual duration may vary based on your pace and rest periods."
        )
    except Exception as e:
        return f"Error calculating workout duration: {str(e)}"


def try_handle_bodyfat_command(user_message: str) -> str | None:
    """Handle body fat calculation. Format: bodyfat <weight> <height> <age> <gender>"""
    text = user_message.lower().strip()
    match = re.match(r"^bodyfat\s+(\d+(\.\d+)?)\s+(\d+(\.\d+)?)\s+(\d+)\s+(male|female)$", text)
    if not match:
        return None
    
    weight = float(match.group(1))
    height = float(match.group(3))
    age = int(match.group(5))
    gender = match.group(6)
    
    try:
        result = calculate_body_fat(weight, height, age, gender)
        return (
            f"**Estimated Body Fat: {result['body_fat']}%**\n\n"
            f"Category: {result['category']}\n\n"
            f"Based on:\n"
            f"- Weight: {weight} kg\n"
            f"- Height: {height} cm\n"
            f"- Age: {age} years\n"
            f"- Gender: {gender}\n\n"
            "This is an estimate using the Deurenberg formula. For accurate body composition analysis, consult a healthcare professional."
        )
    except Exception as e:
        return f"Error calculating body fat: {str(e)}"


def try_handle_idealweight_command(user_message: str) -> str | None:
    """Handle ideal weight calculation. Format: idealweight <height> <gender>"""
    text = user_message.lower().strip()
    match = re.match(r"^idealweight\s+(\d+(\.\d+)?)\s+(male|female)$", text)
    if not match:
        return None
    
    height = float(match.group(1))
    gender = match.group(2)
    
    try:
        result = calculate_ideal_weight(height, gender)
        return (
            f"**Ideal Weight Range for {gender.title()}**\n\n"
            f"Ideal weight: **{result['ideal']} kg**\n"
            f"Healthy range: **{result['min']} - {result['max']} kg**\n\n"
            f"Based on height: {height} cm\n\n"
            "This uses the Robinson formula. Individual ideal weight may vary based on body composition and frame size."
        )
    except Exception as e:
        return f"Error calculating ideal weight: {str(e)}"


def try_handle_protein_command(user_message: str) -> str | None:
    """Handle protein needs calculation. Format: protein <weight> [activity]"""
    text = user_message.lower().strip()
    match = re.match(r"^protein\s+(\d+(\.\d+)?)(?:\s+(sedentary|moderate|active|very_active|athlete))?$", text)
    if not match:
        return None
    
    weight = float(match.group(1))
    activity = match.group(3) or "moderate"
    
    try:
        result = calculate_protein_needs(weight, activity)
        return (
            f"**Daily Protein Needs: {result['protein_grams']} grams**\n\n"
            f"Based on:\n"
            f"- Weight: {weight} kg\n"
            f"- Activity level: {activity}\n\n"
            f"This provides approximately {round(result['protein_grams'] * 4, 0)} calories from protein.\n\n"
            "For muscle building, aim for 1.6-2.2g per kg. For general health, 0.8-1.2g per kg is sufficient."
        )
    except Exception as e:
        return f"Error calculating protein needs: {str(e)}"


def try_handle_water_command(user_message: str) -> str | None:
    """Handle water intake calculation. Format: water <weight> [activity]"""
    text = user_message.lower().strip()
    match = re.match(r"^water\s+(\d+(\.\d+)?)(?:\s+(sedentary|moderate|active|very_active|athlete))?$", text)
    if not match:
        return None
    
    weight = float(match.group(1))
    activity = match.group(3) or "moderate"
    
    try:
        result = calculate_water_intake(weight, activity)
        return (
            f"**Daily Water Intake Recommendation**\n\n"
            f"**{result['liters']} liters** ({result['cups']} cups or {result['ml']} ml)\n\n"
            f"Based on:\n"
            f"- Weight: {weight} kg\n"
            f"- Activity level: {activity}\n\n"
            "Increase intake during hot weather, intense exercise, or if you're pregnant/breastfeeding."
        )
    except Exception as e:
        return f"Error calculating water intake: {str(e)}"


def try_handle_heartrate_command(user_message: str) -> str | None:
    """Handle heart rate zones calculation. Format: heartrate <age>"""
    text = user_message.lower().strip()
    match = re.match(r"^heartrate\s+(\d+)$", text)
    if not match:
        return None
    
    age = int(match.group(1))
    
    try:
        result = calculate_heart_rate_zones(age)
        zones = result['zones']
        return (
            f"**Heart Rate Zones (Age {age})**\n\n"
            f"Maximum Heart Rate: **{result['max_heart_rate']} bpm**\n"
            f"Resting Heart Rate: **{result['resting_heart_rate']} bpm**\n\n"
            f"**Zones:**\n"
            f"- Resting: {zones['resting']['min']}-{zones['resting']['max']} bpm ({zones['resting']['description']})\n"
            f"- Fat Burn: {zones['fat_burn']['min']}-{zones['fat_burn']['max']} bpm ({zones['fat_burn']['description']})\n"
            f"- Cardio: {zones['cardio']['min']}-{zones['cardio']['max']} bpm ({zones['cardio']['description']})\n"
            f"- Aerobic: {zones['aerobic']['min']}-{zones['aerobic']['max']} bpm ({zones['aerobic']['description']})\n"
            f"- Anaerobic: {zones['anaerobic']['min']}-{zones['anaerobic']['max']} bpm ({zones['anaerobic']['description']})\n"
        )
    except Exception as e:
        return f"Error calculating heart rate zones: {str(e)}"


def try_handle_macros_command(user_message: str) -> str | None:
    """Handle macro calculation. Format: macros <calories> [goal]"""
    text = user_message.lower().strip()
    match = re.match(r"^macros\s+(\d+)(?:\s+(weight_loss|muscle_gain|maintain|keto))?$", text)
    if not match:
        return None
    
    calories = float(match.group(1))
    goal = match.group(2) or "maintain"
    
    try:
        result = calculate_macros(calories, goal)
        return (
            f"**Macronutrient Breakdown ({goal.replace('_', ' ').title()})**\n\n"
            f"Total Calories: **{calories}**\n\n"
            f"**Protein:** {result['protein']['grams']}g ({result['protein']['percentage']}%) = {result['protein']['calories']} cal\n"
            f"**Carbs:** {result['carbs']['grams']}g ({result['carbs']['percentage']}%) = {result['carbs']['calories']} cal\n"
            f"**Fat:** {result['fat']['grams']}g ({result['fat']['percentage']}%) = {result['fat']['calories']} cal\n\n"
            f"Total: {result['protein']['calories'] + result['carbs']['calories'] + result['fat']['calories']} calories"
        )
    except Exception as e:
        return f"Error calculating macros: {str(e)}"


def try_handle_tool_commands(user_message: str) -> str | None:
    """
    Try to handle any tool command. Returns result if a tool was used, None otherwise.
    """
    # Try each tool handler in order
    handlers = [
        try_handle_bmi_command,
        try_handle_daily_calories_command,
        try_handle_meal_calories_command,
        try_handle_workout_plan_command,
        try_handle_workout_duration_command,
        try_handle_bodyfat_command,
        try_handle_idealweight_command,
        try_handle_protein_command,
        try_handle_water_command,
        try_handle_heartrate_command,
        try_handle_macros_command,
    ]
    
    for handler in handlers:
        result = handler(user_message)
        if result is not None:
            return result
    
    return None


def _build_conversation_prompt(conversation: List[ChatMessage]) -> str:
    """Combine system prompt with full chat history for grounded answers."""
    prompt_lines = [SYSTEM_PROMPT, ""]

    for message in conversation:
        role_label = "User" if message.role == "user" else "Assistant"
        prompt_lines.append(f"{role_label}: {message.content}")

    prompt_lines.append("Assistant:")
    return "\n".join(prompt_lines)


def generate_response(user_message: str, history: List[ChatMessage] | None = None) -> tuple[str, List[ChatMessage]]:
    """
    Main function called by the API.

    1. Check if the message matches any tool command pattern.
    2. If yes, use the tool and return its answer.
    3. If no, send the message to the LLM with the system prompt.
    4. Log the conversation.
    5. Return the assistant reply along with updated history so callers stay in sync.
    """
    # Normalize incoming history to avoid mutating caller-owned lists.
    conversation_history = list(history or [])

    # Add the newest user message to the working history used for tools and LLM context.
    conversation_history.append(ChatMessage(role="user", content=user_message))
    # 1) Try tool commands first
    tool_reply = try_handle_tool_commands(user_message)
    if tool_reply is not None:
        log_conversation(user_message, tool_reply)
        conversation_history.append(ChatMessage(role="assistant", content=tool_reply))
        return tool_reply, conversation_history

    # 2) Normal LLM-based chat with Gemini
    # Combine system prompt with the entire conversation so Gemini has context.
    full_prompt = _build_conversation_prompt(conversation_history)
    
    model = get_client()
    response = model.generate_content(
        full_prompt,
        generation_config=genai.types.GenerationConfig(
            temperature=0.5,
        )
    )

    assistant_message = response.text.strip()

    # 3) Log conversation
    log_conversation(user_message, assistant_message)

    # 4) Track assistant response in the returned history so the frontend can persist it.
    conversation_history.append(ChatMessage(role="assistant", content=assistant_message))

    return assistant_message, conversation_history

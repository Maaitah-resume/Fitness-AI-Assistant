def calculate_body_fat(weight_kg: float, height_cm: float, age: int, gender: str) -> dict:
    """
    Calculate estimated body fat percentage using Deurenberg formula.
    Returns body fat percentage and category.
    """
    height_m = height_cm / 100.0
    bmi = weight_kg / (height_m ** 2)
    
    # Deurenberg formula
    if gender.lower() == "male":
        body_fat = (1.20 * bmi) + (0.23 * age) - 16.2
    else:
        body_fat = (1.20 * bmi) + (0.23 * age) - 5.4
    
    body_fat = max(5, min(50, body_fat))  # Clamp between 5% and 50%
    
    # Categories
    if gender.lower() == "male":
        if body_fat < 6:
            category = "Essential fat"
        elif body_fat < 14:
            category = "Athletes"
        elif body_fat < 18:
            category = "Fitness"
        elif body_fat < 25:
            category = "Average"
        else:
            category = "Obese"
    else:
        if body_fat < 14:
            category = "Essential fat"
        elif body_fat < 20:
            category = "Athletes"
        elif body_fat < 25:
            category = "Fitness"
        elif body_fat < 32:
            category = "Average"
        else:
            category = "Obese"
    
    return {
        "body_fat": round(body_fat, 1),
        "category": category
    }


def calculate_ideal_weight(height_cm: float, gender: str) -> dict:
    """
    Calculate ideal weight range using Robinson formula.
    Returns min and max ideal weight in kg.
    """
    height_inches = height_cm / 2.54
    
    if gender.lower() == "male":
        # Robinson formula for men
        ideal_kg = 52 + 1.9 * (height_inches - 60)
    else:
        # Robinson formula for women
        ideal_kg = 49 + 1.7 * (height_inches - 60)
    
    # Range: ±10% of ideal weight
    min_weight = round(ideal_kg * 0.9, 1)
    max_weight = round(ideal_kg * 1.1, 1)
    ideal = round(ideal_kg, 1)
    
    return {
        "ideal": ideal,
        "min": min_weight,
        "max": max_weight
    }


def calculate_protein_needs(weight_kg: float, activity_level: str = "moderate") -> dict:
    """
    Calculate daily protein needs based on weight and activity level.
    Returns protein in grams.
    """
    # Base protein: 0.8g per kg (sedentary)
    base_protein = weight_kg * 0.8
    
    # Activity multipliers
    multipliers = {
    "sedentary": 1.0,
    "moderate": 1.2,
    "active": 1.4,
    "very_active": 1.6,
    "athlete": 2.0,
    "bodybuilder": 2.2,
    "cutting_phase": 1.8,
}

    
    multiplier = multipliers.get(activity_level.lower(), 1.2)
    protein_grams = round(base_protein * multiplier, 1)
    
    return {
        "protein_grams": protein_grams,
        "activity_level": activity_level
    }


def calculate_water_intake(weight_kg: float, activity_level: str = "moderate") -> dict:
    """
    Calculate daily water intake in liters.
    Base: 35ml per kg body weight, plus activity adjustment.
    """
    base_water_ml = weight_kg * 35
    
    # Activity adjustments (additional ml per hour of activity)
    activity_additions = {
    "sedentary": 0,
    "moderate": 500,
    "active": 1000,
    "very_active": 1500,
    "athlete": 2000,
    "hot_weather": 800,
    "intense_training": 2000,
}

    
    additional = activity_additions.get(activity_level.lower(), 500)
    total_ml = base_water_ml + additional
    total_liters = round(total_ml / 1000, 2)
    cups = round(total_liters * 4.2, 1)  # 1 cup ≈ 240ml
    
    return {
        "liters": total_liters,
        "cups": cups,
        "ml": round(total_ml, 0)
    }


def calculate_heart_rate_zones(age: int) -> dict:
    """
    Calculate heart rate zones based on age.
    Returns all zones: resting, fat burn, cardio, peak.
    """
    max_hr = 220 - age
    resting_hr = 60  # Average resting heart rate
    
    zones ={
    "resting": { "min": 60, "max": round(max_hr * 0.5), "description": "Rest and recovery" },
    "fat_burn": { "min": round(max_hr * 0.5), "max": round(max_hr * 0.6), "description": "Fat burning zone" },
    "cardio":   { "min": round(max_hr * 0.6), "max": round(max_hr * 0.7), "description": "Cardio endurance" },
    "aerobic":  { "min": round(max_hr * 0.7), "max": round(max_hr * 0.85), "description": "Aerobic training" },
    "anaerobic":{ "min": round(max_hr * 0.85), "max": max_hr, "description": "Near-max training" },
    "vo2_max":  { "min": max_hr - 5, "max": max_hr, "description": "Elite conditioning" }
}
    
    return {
        "max_heart_rate": max_hr,
        "resting_heart_rate": resting_hr,
        "zones": zones
    }


def calculate_macros(total_calories: float, goal: str = "maintain") -> dict:
    """
    Calculate macronutrient breakdown based on total calories and goal.
    Returns protein, carbs, and fat in grams and percentages.
    """
    # Macro ratios based on goal
    ratios = {
        "weight_loss": {"protein": 0.30, "carbs": 0.40, "fat": 0.30},
        "muscle_gain": {"protein": 0.30, "carbs": 0.50, "fat": 0.20},
        "maintain": {"protein": 0.25, "carbs": 0.45, "fat": 0.30},
        "keto": {"protein": 0.25, "carbs": 0.05, "fat": 0.70}
    }
    
    ratio = ratios.get(goal.lower(), ratios["maintain"])
    
    # Calories per gram: protein=4, carbs=4, fat=9
    protein_cals = total_calories * ratio["protein"]
    carbs_cals = total_calories * ratio["carbs"]
    fat_cals = total_calories * ratio["fat"]
    
    protein_grams = round(protein_cals / 4, 1)
    carbs_grams = round(carbs_cals / 4, 1)
    fat_grams = round(fat_cals / 9, 1)
    
    return {
        "goal": goal,
        "protein": {
            "grams": protein_grams,
            "calories": round(protein_cals, 0),
            "percentage": round(ratio["protein"] * 100, 1)
        },
        "carbs": {
            "grams": carbs_grams,
            "calories": round(carbs_cals, 0),
            "percentage": round(ratio["carbs"] * 100, 1)
        },
        "fat": {
            "grams": fat_grams,
            "calories": round(fat_cals, 0),
            "percentage": round(ratio["fat"] * 100, 1)
        }
    }


CALORIE_TABLE = {
    "apple": 95,  # medium (182g)
    "banana": 105,  # medium (118g)
    "blueberries": 85,  # 1 cup
    "strawberries": 50,  # 1 cup sliced
    "orange": 62,  # medium
    "grapes": 62,  # 1 cup
    "avocado": 240,  # 1 whole
    "almonds": 170,  # 1 oz (~23 nuts)
    "peanut butter": 190,  # 2 tbsp
    "egg": 78,  # large
    "greek yogurt": 130,  # 1 cup, nonfat plain
    "cottage cheese": 163,  # 1 cup low-fat
    "chicken breast": 165,  # 3 oz cooked, skinless
    "salmon": 208,  # 3 oz cooked
    "tofu": 180,  # 1/2 cup firm
    "black beans": 227,  # 1 cup cooked
    "quinoa": 222,  # 1 cup cooked
    "brown rice": 216,  # 1 cup cooked
    "white rice": 205,  # 1 cup cooked
    "oatmeal": 150,  # 1 cup cooked
    "sweet potato": 112,  # medium baked
    "broccoli": 55,  # 1 cup chopped
    "spinach": 7,  # 1 cup raw
    "olive oil": 119,  # 1 tbsp
    "whole wheat bread": 110,  # 1 slice
    "protein powder": 120,  # 1 scoop
}

MACRO_RATIOS = {
    "weight_loss": {"protein": 35, "carbs": 35, "fat": 30},
    "muscle_gain": {"protein": 30, "carbs": 45, "fat": 25},
    "maintenance": {"protein": 25, "carbs": 50, "fat": 25},
    "low_carb": {"protein": 40, "carbs": 25, "fat": 35},
    "endurance": {"protein": 20, "carbs": 55, "fat": 25},
}

PLAN_TABLE = {
    "high_protein_breakfast": "Greek yogurt, berries, and almond butter",
    "balanced_lunch": "Grilled chicken, quinoa, broccoli, and olive oil",
    "plant_powered_dinner": "Tofu stir-fry with brown rice and spinach",
    "post_workout": "Protein powder shake with banana",
    "light_snack": "Apple slices with peanut butter",
}


def calculate_daily_calories(weight, height, age, gender, activity_level):
    """
    Simple calorie calculator using Mifflin-St Jeor Equation.
    """
    if gender.lower() == "male":
        bmr = 10 * weight + 6.25 * height - 5 * age + 5
    else:
        bmr = 10 * weight + 6.25 * height - 5 * age - 161

    factors = {
        "low": 1.2,
        "medium": 1.55,
        "high": 1.9
    }

    return round(bmr * factors.get(activity_level, 1.2), 2)


def estimate_meal_calories(meal_items: list):
    """
    Estimate calories from a list of items using a local lookup table.
    The function returns only the total, but richer tables are kept
    module-wide for reuse in nutritional explanations.
    """
    total = 0
    for item in meal_items:
        total += CALORIE_TABLE.get(item.lower(), 0)

    return total

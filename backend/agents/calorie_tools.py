CALORIE_TABLE = {
    # Fruits
    "apple": 95,
    "banana": 105,
    "orange": 62,
    "pear": 102,
    "grapes": 62,
    "pineapple": 82,
    "mango": 135,
    "watermelon": 85,
    "blueberries": 85,
    "strawberries": 50,
    "kiwi": 42,
    "peach": 59,
    "apricot": 17,
    "plum": 30,
    "dates": 20,  # per date
    "fig": 37,
    "pomegranate": 234,
    "cherries": 97,
    "avocado": 240,

    # Vegetables
    "broccoli": 55,
    "carrot": 25,
    "spinach": 7,
    "kale": 35,
    "zucchini": 33,
    "cucumber": 16,
    "lettuce": 5,
    "tomato": 22,
    "bell pepper": 30,
    "onion": 40,
    "garlic": 4,
    "potato": 160,
    "sweet potato": 112,
    "green beans": 44,
    "peas": 134,
    "corn": 96,

    # Proteins
    "egg": 78,
    "egg white": 17,
    "chicken breast": 165,
    "chicken thigh": 209,
    "turkey breast": 135,
    "beef steak": 242,
    "ground beef": 250,
    "salmon": 208,
    "tuna": 179,
    "shrimp": 84,
    "tilapia": 111,
    "tofu": 180,
    "tempeh": 195,
    "black beans": 227,
    "lentils": 230,
    "chickpeas": 269,
    "kidney beans": 215,

    # Carbs / Grains
    "white rice": 205,
    "brown rice": 216,
    "quinoa": 222,
    "oatmeal": 150,
    "pasta": 221,
    "whole wheat bread": 110,
    "bagel": 245,
    "tortilla": 140,
    "cereal": 200,

    # Snacks
    "almonds": 170,
    "peanuts": 166,
    "walnuts": 185,
    "mixed nuts": 175,
    "granola bar": 135,
    "protein bar": 210,
    "popcorn": 55,
    "chips": 152,
    "pretzels": 108,
    "peanut butter": 190,

    # Dairy
    "greek yogurt": 130,
    "cottage cheese": 163,
    "milk": 103,
    "cheddar cheese": 113,
    "mozzarella": 85,
    "swiss cheese": 108,
    "cream cheese": 99,

    # Oils / Condiments
    "olive oil": 119,
    "butter": 102,
    "mayonnaise": 94,
    "honey": 64,
    "jam": 56,
    "ketchup": 20,
    "mustard": 5,
    "soy sauce": 8,
    "ranch": 145,

    # Desserts
    "ice cream": 207,
    "chocolate": 155,
    "cookie": 78,
    "cake": 235,
    "brownie": 132,
    "donut": 195,
}


MACRO_RATIOS = {
    "weight_loss": {"protein": 35, "carbs": 35, "fat": 30},
    "muscle_gain": {"protein": 30, "carbs": 45, "fat": 25},
    "maintenance": {"protein": 25, "carbs": 50, "fat": 25},
    "low_carb": {"protein": 40, "carbs": 25, "fat": 35},
    "endurance": {"protein": 20, "carbs": 55, "fat": 25},
    "keto": {"protein": 25, "carbs": 5, "fat": 70},
    "high_protein": {"protein": 45, "carbs": 30, "fat": 25},
}


PLAN_TABLE = {
    "high_protein_breakfast": "Greek yogurt, berries, almond butter, eggs, cottage cheese",
    "balanced_lunch": "Grilled chicken, quinoa, broccoli, avocado, olive oil",
    "plant_powered_dinner": "Tofu, stir-fry vegetables, brown rice, spinach",
    "post_workout": "Protein shake, banana, almond butter",
    "light_snack": "Apple slices with peanut butter",
    "low_carb_lunch": "Grilled salmon, asparagus, mixed greens",
    "bulking_meal": "Beef steak, sweet potato, rice, nuts",
    "cutting_dinner": "Lean turkey, steamed veggies, salad",
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

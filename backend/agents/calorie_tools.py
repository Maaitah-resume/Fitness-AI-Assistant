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
    """
    calorie_table = {
        "apple": 95,
        "banana": 110,
        "chicken breast": 165,
        "rice": 200,
        "salad": 33,
    }

    total = 0
    for item in meal_items:
        total += calorie_table.get(item.lower(), 0)

    return total

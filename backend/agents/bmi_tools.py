def calculate_bmi(weight_kg: float, height_cm: float) -> dict:
    """
    Calculate BMI and return a dict with:
    - bmi_value: float
    - category: str
    """
    # Convert height from cm to meters
    height_m = height_cm / 100.0

    # Protect against division by zero or invalid height
    if height_m <= 0:
        return {
            "bmi_value": None,
            "category": "invalid_height",
        }

    bmi = weight_kg / (height_m ** 2)

    # Simple BMI categories
    if bmi < 18.5:
        category = "underweight"
    elif bmi < 25:
        category = "normal"
    elif bmi < 30:
        category = "overweight"
    else:
        category = "obese"

    return {
        "bmi_value": round(bmi, 1),
        "category": category,
    }


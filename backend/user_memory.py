import json
import os

MEMORY_FILE = os.path.join(os.path.dirname(__file__), "memory", "user_profile.json")

# Ensure the memory folder exists
MEMORY_DIR = os.path.dirname(MEMORY_FILE)
if not os.path.exists(MEMORY_DIR):
    os.makedirs(MEMORY_DIR)


def load_profile():
    if not os.path.exists(MEMORY_FILE):
        return {}
    with open(MEMORY_FILE, "r") as f:
        try:
            return json.load(f)
        except:
            return {}


def save_profile(profile):
    with open(MEMORY_FILE, "w") as f:
        json.dump(profile, f, indent=4)


def update_profile(key, value):
    profile = load_profile()
    profile[key] = value
    save_profile(profile)


def get_profile():
    return load_profile()


def missing_fields():
    required = [
        "age", "weight", "height", "gender",
        "goal", "level", "training_days", "equipment"
    ]
    profile = load_profile()
    return [field for field in required if field not in profile or not profile[field]]


def reset_profile():
    save_profile({})


def auto_reset_on_start():
    save_profile({})


# Run on app startup
auto_reset_on_start()

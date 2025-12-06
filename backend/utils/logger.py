import csv
import os
import datetime

LOG_PATH = os.path.join('data', 'logs.csv')

def log_conv(user_input: str, ai_response: str) -> None:
    """
    Append one conversation pair to data/logs.csv.
    If the file does not exist, create it and write a header row.
    """
    os.makedirs(os.path.dirname(LOG_PATH), exist_ok=True)
    file_exists = os.path.exists(LOG_PATH)

    with open(LOG_PATH, mode = 'a', newline = '', encoding = 'utf-8') as file:
        writer = csv.writer(file)
        if not file_exists:
            writer.writerow(['Timestamp', 'User Input', 'AI Response'])
        writer.writerow([datetime.datetime.now().isoformat(), user_input, ai_response])

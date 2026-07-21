from __future__ import annotations

import os
import time
from datetime import datetime

import schedule
from dotenv import load_dotenv

from main import run

load_dotenv()

RUN_TIME = os.getenv("DAILY_RUN_TIME", "08:00")

TOPICS = [
    "Codex có thể tự sửa bug như thế nào?",
    "Docker là gì và vì sao lập trình viên dùng nó?",
    "AI agent khác chatbot ở điểm nào?",
    "Git rebase là gì?",
    "Ba xu hướng AI coding đáng theo dõi",
]


def daily_job() -> None:
    day_index = datetime.now().toordinal() % len(TOPICS)
    topic = TOPICS[day_index]
    try:
        run(topic)
    except Exception as exc:
        print(f"Daily job failed: {exc}")


if __name__ == "__main__":
    print(f"Scheduler đang chạy. Job hằng ngày lúc {RUN_TIME}.")
    schedule.every().day.at(RUN_TIME).do(daily_job)

    while True:
        schedule.run_pending()
        time.sleep(15)

import time
import logging
import json
import subprocess
import os
from .settings import settings
from .queues import claim_next_job, complete_job, fail_job

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger(__name__)

# Assume worker runs in TechFlow/worker-python/app, so project root is two levels up
PROJECT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WORKER_SCRIPT = os.path.join(PROJECT_DIR, "video_worker.py")
PYTHON_CMD = "python"

def run_legacy_script(args: list) -> str:
    """Run the legacy video_worker.py script and return the JSON output."""
    cmd = [PYTHON_CMD, WORKER_SCRIPT] + args
    logger.info(f"Running command: {' '.join(cmd)}")
    
    result = subprocess.run(
        cmd,
        cwd=PROJECT_DIR,
        capture_output=True,
        text=True
    )
    
    if result.returncode != 0:
        error_tail = result.stderr[-2000:] if result.stderr else result.stdout[-2000:]
        raise Exception(f"Worker script failed with exit code {result.returncode}: {error_tail}")
        
    return result.stdout

def extract_json_from_output(output: str) -> dict:
    """The legacy script prints logs and then a final JSON string bounded by ===WORKER_OUTPUT===."""
    marker = "===WORKER_OUTPUT==="
    if marker in output:
        json_str = output.split(marker)[-1].strip()
        return json.loads(json_str)
    
    # Fallback to finding the last line that looks like JSON
    for line in reversed(output.splitlines()):
        line = line.strip()
        if line.startswith("{") and line.endswith("}"):
            try:
                return json.loads(line)
            except:
                pass
    raise Exception("Could not find valid JSON output from worker script")

def process_generate_character(job_id: int, input_data: dict):
    # input_data: {"campaign_id": 4, "prompt": "...", "theme": "...", "visual_style": "..."}
    args = [
        "--topic", str(input_data.get("theme", "")),
        "--visual-style", str(input_data.get("visual_style", "")),
        "--character", str(input_data.get("prompt", "")),
        "--generate-character"
    ]
    output = run_legacy_script(args)
    # The character script just prints the URL
    url = ""
    for line in reversed(output.splitlines()):
        if line.startswith("CHARACTER_IMAGE_URL="):
            url = line.split("=", 1)[1].strip()
            break
            
    if not url:
        raise Exception("Did not find CHARACTER_IMAGE_URL in script output")
        
    return {"character_image_url": url}

def process_generate_script(job_id: int, input_data: dict):
    args = [
        "--topic", str(input_data.get("topic", "")),
        "--duration", str(input_data.get("duration", 60)),
    ]
    
    if input_data.get("visual_style"):
        args.extend(["--visual-style", str(input_data.get("visual_style"))])
    if input_data.get("character"):
        args.extend(["--character", str(input_data.get("character"))])
    if input_data.get("character_image"):
        args.extend(["--character-image", str(input_data.get("character_image"))])
        
    args.extend([
        "--audio-mode", str(input_data.get("audio_mode", "ai")),
        "--video-provider", str(input_data.get("video_provider", "runway")),
        "--aspect-ratio", str(input_data.get("aspect_ratio", "9:16")),
        "--render-quality", str(input_data.get("render_quality", "720p")),
        "--generate-script"
    ])
    
    output = run_legacy_script(args)
    return extract_json_from_output(output)

def process_generate_video(job_id: int, input_data: dict):
    args = [
        "--topic", str(input_data.get("topic", "")),
        "--duration", str(input_data.get("duration", 60)),
    ]
    
    if input_data.get("visual_style"):
        args.extend(["--visual-style", str(input_data.get("visual_style"))])
    if input_data.get("character"):
        args.extend(["--character", str(input_data.get("character"))])
    if input_data.get("character_image"):
        args.extend(["--character-image", str(input_data.get("character_image"))])
        
    args.extend([
        "--audio-mode", str(input_data.get("audio_mode", "ai")),
        "--video-provider", str(input_data.get("video_provider", "runway")),
        "--aspect-ratio", str(input_data.get("aspect_ratio", "9:16")),
        "--render-quality", str(input_data.get("render_quality", "720p"))
    ])
    
    output = run_legacy_script(args)
    return extract_json_from_output(output)

def process_job(job):
    job_id = job["id"]
    job_type = job["job_type"]
    
    logger.info(f"Processing job {job_id} of type {job_type}")
    
    try:
        input_data = {}
        if job.get("input_json"):
            input_data = json.loads(job["input_json"])
            
        if job_type == "GENERATE_CHARACTER":
            result = process_generate_character(job_id, input_data)
        elif job_type == "GENERATE_SCRIPT":
            result = process_generate_script(job_id, input_data)
        elif job_type == "GENERATE_VIDEO":
            result = process_generate_video(job_id, input_data)
        else:
            raise Exception(f"Unknown job type: {job_type}")
            
        output_json = json.dumps(result)
        complete_job(job_id, output_json)
        logger.info(f"Successfully completed job {job_id}")
    except Exception as e:
        logger.error(f"Failed to process job {job_id}: {e}")
        fail_job(job_id, str(e))

def main():
    logger.info(f"Starting TechFlow Worker Daemon (Worker ID: {settings.worker_id})")
    while True:
        try:
            job = claim_next_job()
            if job:
                process_job(job)
            else:
                time.sleep(settings.poll_interval_seconds)
        except Exception as e:
            logger.error(f"Worker loop error: {e}")
            time.sleep(settings.poll_interval_seconds)

if __name__ == "__main__":
    main()

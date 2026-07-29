import json
import logging
from contextlib import contextmanager
from typing import Dict, Any, Optional

import psycopg2
from psycopg2.extras import RealDictCursor

from .settings import settings

logger = logging.getLogger(__name__)

@contextmanager
def get_db_connection():
    conn = psycopg2.connect(settings.db_url)
    try:
        yield conn
    finally:
        conn.close()

def claim_next_job() -> Optional[Dict[str, Any]]:
    """
    Finds the next QUEUED job, locks it (SKIP LOCKED) to prevent other workers
    from taking it, and updates its status to RUNNING atomically.
    """
    query = """
        UPDATE generation_jobs
        SET status = 'RUNNING',
            worker_id = %s,
            started_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = (
            SELECT id FROM generation_jobs
            WHERE status = 'QUEUED'
            ORDER BY priority DESC, queued_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
        )
        RETURNING id, job_type, input_json, episode_id, workflow_run_id;
    """
    with get_db_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(query, (settings.worker_id,))
            job = cur.fetchone()
            conn.commit()
            if job:
                return dict(job)
    return None

def complete_job(job_id: int, output_json: str):
    query = """
        UPDATE generation_jobs
        SET status = 'COMPLETED',
            output_json = %s,
            finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = %s;
    """
    with get_db_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(query, (output_json, job_id))
            conn.commit()

def fail_job(job_id: int, error_message: str):
    query = """
        UPDATE generation_jobs
        SET status = 'FAILED',
            error_message = %s,
            finished_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = %s;
    """
    with get_db_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(query, (error_message, job_id))
            conn.commit()

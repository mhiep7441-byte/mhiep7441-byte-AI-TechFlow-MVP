import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    db_url: str = os.getenv("DATABASE_URL", "postgresql://user:password@localhost:5432/techflow")
    worker_id: str = os.getenv("RENDER_INSTANCE_ID", "local-worker")
    poll_interval_seconds: int = 5
    cloudinary_url: str = os.getenv("CLOUDINARY_URL", "")
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    
    class Config:
        env_file = ".env"

settings = Settings()

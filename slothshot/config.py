"""Configuration settings for SlothShot."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings with environment variable fallbacks."""
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # App info
    app_name: str = "SlothShot"
    app_version: str = "1.0.0"
    debug: bool = False

    # Directories
    base_dir: Path = Path(__file__).resolve().parent.parent
    data_dir: Path = base_dir / "data"
    screenshots_dir: Path = data_dir / "screenshots"
    trash_dir: Path = data_dir / "trash"
    archive_dir: Path = data_dir / "archive"

    # Android ADB Configuration
    android_screenshot_path: str = "/sdcard/Pictures/Screenshots"
    adb_binary: str = "adb"

    # Gemma / Google AI configuration
    gemini_api_key: Optional[str] = os.getenv("GEMINI_API_KEY", None)
    gemma_model_name: str = "gemma-2-9b-it"  # or gemini-2.5-flash / local ollama

    # Sentry Configuration (Agent Tracing)
    sentry_dsn: Optional[str] = os.getenv("SENTRY_DSN", None)
    sentry_environment: str = os.getenv("ENVIRONMENT", "development")
    sentry_traces_sample_rate: float = 1.0

    # ElevenLabs Audio Briefing
    elevenlabs_api_key: Optional[str] = os.getenv("ELEVENLABS_API_KEY", None)
    elevenlabs_voice_id: str = "pNInz6obpgDQGcFmaJgB"  # Adam / Calm voice

    # TabPFN Model Configuration
    tabpfn_token: Optional[str] = os.getenv("TABPFN_TOKEN", None)
    tabpfn_device: str = "cpu"
    tabpfn_n_estimators: int = 8

    def ensure_directories(self) -> None:
        """Create necessary operational directories."""
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.trash_dir.mkdir(parents=True, exist_ok=True)
        self.archive_dir.mkdir(parents=True, exist_ok=True)


settings = Settings()
settings.ensure_directories()

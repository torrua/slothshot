"""FastAPI Mobile-First Web Application & REST API for SlothShot."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import List, Optional
import aiofiles
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from slothshot.adb_client import AndroidADBClient
from slothshot.config import settings
from slothshot.elevenlabs_briefing import ElevenLabsVoiceButler
from slothshot.engine import SlothEngine
from slothshot.models import TriageResult
from slothshot.telemetry import init_telemetry

logger = logging.getLogger("slothshot.web")

# Initialize Sentry Agent Tracing
init_telemetry()

app = FastAPI(
    title="SlothShot - The Zero-Effort Android Screenshot Butler",
    description="Mobile-First Open-Source AI Screenshot Triage powered by TabPFN & Gemma 2",
    version=settings.app_version,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Engine & Services
engine = SlothEngine()
adb_client = AndroidADBClient()
voice_butler = ElevenLabsVoiceButler()

# Mount Static & Templates
web_dir = Path(__file__).parent
static_dir = web_dir / "static"
static_dir.mkdir(parents=True, exist_ok=True)
app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")


@app.on_event("startup")
async def startup_event():
    """Auto-load and triage existing screenshots on server launch."""
    if settings.screenshots_dir.exists():
        logger.info(f"Auto-processing screenshots in {settings.screenshots_dir}...")
        engine.process_directory(settings.screenshots_dir)


class MetricsSummary(BaseModel):
    total_screenshots: int
    purged_count: int
    bytes_reclaimed: int
    megabytes_reclaimed: float
    estimated_minutes_saved: float
    movies_found: int
    books_found: int
    apps_found: int
    wifi_saved: int


@app.get("/", response_class=HTMLResponse)
async def serve_index():
    """Serve mobile-optimized PWA UI."""
    template_path = web_dir / "templates" / "index.html"
    if template_path.exists():
        with open(template_path, "r", encoding="utf-8") as f:
            return HTMLResponse(content=f.read())
    return HTMLResponse("<h1>SlothShot Web Interface Loading...</h1>")


@app.get("/manifest.json")
async def serve_manifest():
    """Serve PWA manifest for Android homescreen installation."""
    manifest_path = static_dir / "manifest.json"
    if manifest_path.exists():
        return FileResponse(str(manifest_path), media_type="application/manifest+json")
    return {"name": "SlothShot", "short_name": "SlothShot", "start_url": "/"}


@app.get("/api/results", response_model=List[TriageResult])
async def get_results(tag: Optional[str] = None):
    """Retrieve all processed screenshot results, optionally filtered by tag."""
    results = engine.get_results()
    if tag:
        tag_clean = tag if tag.startswith("#") else f"#{tag}"
        results = [r for r in results if tag_clean in r.tags]
    return results


@app.post("/api/upload", response_model=TriageResult)
async def upload_screenshot(file: UploadFile = File(...)):
    """Upload and process screenshot from Android or browser."""
    if not file.filename:
        raise HTTPException(status_code=400, detail="Filename required")

    destination = settings.screenshots_dir / file.filename
    async with aiofiles.open(destination, "wb") as buffer:
        content = await file.read()
        await buffer.write(content)

    result = engine.process_screenshot(destination)
    return result


@app.post("/api/purge/{result_id}")
async def purge_item(result_id: str):
    """Purge a single expired/extracted screenshot."""
    success = engine.purge_screenshot(result_id)
    if not success:
        raise HTTPException(status_code=404, detail="Item not found or already purged")
    return {"status": "purged", "id": result_id}


@app.post("/api/purge-all-lazy")
async def purge_all_lazy():
    """The Ultimate Sloth Action: 1-click purge of all extract_and_purge and ephemeral junk."""
    results = engine.get_results()
    purged_ids = []
    reclaimed_bytes = 0

    for r in results:
        if r.status == "analyzed" and r.retention_class.value in ["extract_and_purge", "ephemeral_junk"]:
            if engine.purge_screenshot(r.id):
                purged_ids.append(r.id)
                reclaimed_bytes += r.bytes_can_be_reclaimed

    return {
        "status": "success",
        "purged_count": len(purged_ids),
        "reclaimed_bytes": reclaimed_bytes,
        "reclaimed_mb": round(reclaimed_bytes / (1024 * 1024), 2),
    }


@app.post("/api/archive/{result_id}")
async def archive_item(result_id: str):
    """Archive screenshot with clean semantic name."""
    success = engine.archive_screenshot(result_id)
    if not success:
        raise HTTPException(status_code=404, detail="Item not found")
    return {"status": "archived", "id": result_id}


@app.post("/api/sync-adb")
async def sync_from_android():
    """Trigger ADB sync from connected Android device."""
    devices = adb_client.list_connected_devices()
    pulled = adb_client.pull_screenshots(settings.screenshots_dir, limit=15)
    processed = []
    for p in pulled:
        try:
            res = engine.process_screenshot(p)
            processed.append(res)
        except Exception as e:
            logger.error(f"Error processing {p}: {e}")

    return {
        "connected_devices": devices,
        "pulled_count": len(pulled),
        "processed_count": len(processed),
    }


@app.get("/api/metrics", response_model=MetricsSummary)
async def get_metrics():
    """Return accumulated Sloth Payoff metrics."""
    results = engine.get_results()
    total = len(results)
    purged = sum(1 for r in results if r.status == "purged")
    bytes_reclaimed = sum(r.bytes_can_be_reclaimed for r in results if r.status == "purged")

    movies = sum(len(r.entities.movies) for r in results)
    books = sum(len(r.entities.books) for r in results)
    apps = sum(len(r.entities.apps) for r in results)
    wifis = sum(1 for r in results if r.entities.wifi)

    minutes_saved = (total * 75) / 60.0

    return MetricsSummary(
        total_screenshots=total,
        purged_count=purged,
        bytes_reclaimed=bytes_reclaimed,
        megabytes_reclaimed=round(bytes_reclaimed / (1024 * 1024), 2),
        estimated_minutes_saved=round(minutes_saved, 1),
        movies_found=movies,
        books_found=books,
        apps_found=apps,
        wifi_saved=wifis,
    )


@app.get("/api/audio-briefing")
async def get_audio_briefing():
    """Generate or retrieve ElevenLabs voice briefing."""
    results = engine.get_results()
    total_bytes = sum(r.bytes_can_be_reclaimed for r in results)
    text = voice_butler.compose_briefing_text(results, total_bytes)
    audio_path = settings.data_dir / "sloth_briefing.mp3"
    voice_butler.generate_audio(text, audio_path)

    if audio_path.exists():
        return FileResponse(str(audio_path), media_type="audio/mpeg", filename="sloth_briefing.mp3")
    return {"message": text}


class GeminiKeyRequest(BaseModel):
    api_key: str
    model_name: Optional[str] = "gemini-2.5-flash"


@app.get("/api/settings")
async def get_settings_status():
    """Get current AI engine configuration status."""
    has_gemini = bool(settings.gemini_api_key and settings.gemini_api_key.strip())
    masked = None
    if has_gemini:
        k = settings.gemini_api_key.strip()
        masked = f"{k[:4]}...{k[-4:]}" if len(k) > 8 else "****"

    return {
        "gemini_configured": has_gemini,
        "gemini_masked_key": masked,
        "model_name": settings.gemma_model_name,
        "engine_mode": f"{settings.gemma_model_name} (Multimodal Cloud AI)" if has_gemini else "Local Offline (RapidOCR + TabPFN + Heuristics)",
        "offline_ready": True,
    }


@app.post("/api/settings/gemini-key")
async def update_gemini_key(req: GeminiKeyRequest):
    """Save and activate Gemini API key, persisting to .env."""
    key = req.api_key.strip()
    if not key:
        settings.gemini_api_key = None
        engine.gemma = GemmaSemanticExtractor(api_key=None)
        return {"status": "cleared", "mode": "Local Offline"}

    # Validate key by initializing client and performing a test call
    try:
        from google import genai
        test_client = genai.Client(api_key=key)
        model = req.model_name or settings.gemma_model_name or "gemini-2.5-flash"
        test_res = test_client.models.generate_content(
            model=model,
            contents="Say 'OK' in one word.",
        )
        logger.info(f"Gemini API key validation response: {getattr(test_res, 'text', 'OK').strip()}")
    except Exception as e:
        logger.warning(f"Gemini key validation failed: {e}")
        raise HTTPException(status_code=400, detail=f"Invalid Gemini API Key or connection error: {e}")

    settings.gemini_api_key = key
    if req.model_name:
        settings.gemma_model_name = req.model_name
    engine.gemma = GemmaSemanticExtractor(api_key=key)

    # Persist to .env
    env_file = settings.base_dir / ".env"
    try:
        lines = []
        if env_file.exists():
            with open(env_file, "r", encoding="utf-8") as f:
                lines = f.readlines()
        new_lines = []
        key_found = False
        model_found = False
        for line in lines:
            if line.startswith("GEMINI_API_KEY="):
                new_lines.append(f"GEMINI_API_KEY={key}\n")
                key_found = True
            elif line.startswith("GEMMA_MODEL_NAME="):
                new_lines.append(f"GEMMA_MODEL_NAME={settings.gemma_model_name}\n")
                model_found = True
            else:
                new_lines.append(line)
        if not key_found:
            new_lines.append(f"GEMINI_API_KEY={key}\n")
        if not model_found:
            new_lines.append(f"GEMMA_MODEL_NAME={settings.gemma_model_name}\n")
        with open(env_file, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
    except Exception as env_err:
        logger.warning(f"Could not persist .env: {env_err}")

    return {
        "status": "success",
        "message": f"Gemini API Key activated with model {settings.gemma_model_name}!",
        "engine_mode": f"{settings.gemma_model_name} (Multimodal Cloud AI)",
    }


@app.post("/api/reprocess-all")
async def reprocess_all_screenshots():
    """Re-analyze all screenshots using the active AI engine."""
    results = engine.process_directory(settings.screenshots_dir)
    return {
        "status": "success",
        "reprocessed_count": len(results),
        "engine_mode": "Gemini Multimodal" if settings.gemini_api_key else "Local Offline",
    }

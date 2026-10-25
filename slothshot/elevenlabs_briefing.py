"""ElevenLabs Voice synthesis for SlothShot sleepy audio briefings."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import List, Optional
import httpx

from slothshot.config import settings
from slothshot.models import TriageResult
from slothshot.telemetry import trace_agent_span

logger = logging.getLogger("slothshot.voice")


class ElevenLabsVoiceButler:
    """Generates calming, sleepy audio updates from the Sloth butler."""

    def __init__(self, api_key: Optional[str] = None, voice_id: Optional[str] = None):
        self.api_key = api_key or settings.elevenlabs_api_key
        self.voice_id = voice_id or settings.elevenlabs_voice_id
        self.api_url = f"https://api.elevenlabs.io/v1/text-to-speech/{self.voice_id}"

    def compose_briefing_text(self, results: List[TriageResult], total_bytes_saved: int) -> str:
        """Compose human-friendly, mildly sleepy summary script."""
        total_count = len(results)
        movies = []
        books = []
        apps = []
        wifis = []
        purged = 0

        for r in results:
            if r.entities.movies:
                movies.extend([m.title for m in r.entities.movies])
            if r.entities.books:
                books.extend([b.title for b in r.entities.books])
            if r.entities.apps:
                apps.extend([a.name for a in r.entities.apps])
            if r.entities.wifi:
                wifis.append(r.entities.wifi.ssid)
            if r.retention_class.value in ["extract_and_purge", "ephemeral_junk"]:
                purged += 1

        mb_saved = total_bytes_saved / (1024 * 1024)
        script_parts = [
            f"Hey sloth. While you were taking it easy, I sorted through {total_count} of your Android screenshots."
        ]

        highlights = []
        if movies:
            highlights.append(f"spotted {len(movies)} movies to watch, including {movies[0]}")
        if books:
            highlights.append(f"saved {len(books)} book recommendations, like {books[0]}")
        if apps:
            highlights.append(f"cataloged {len(apps)} apps to check out")
        if wifis:
            highlights.append(f"stored Wi-Fi credentials for {wifis[0]}")

        if highlights:
            script_parts.append(f"I {', and I '.join(highlights)}.")

        if purged > 0:
            script_parts.append(
                f"I safely queued {purged} expired screenshots for deletion, reclaiming {mb_saved:.1f} megabytes of storage."
            )

        script_parts.append("All useful links and passwords are ready in your pocket. Go back to sleep.")
        return " ".join(script_parts)

    @trace_agent_span(op="ai.elevenlabs.tts", description="ElevenLabs Sleepy Audio Generation")
    def generate_audio(self, text: str, output_path: Path) -> Path:
        """Generate MP3 audio file via ElevenLabs API (or fallback audio stub)."""
        output_path.parent.mkdir(parents=True, exist_ok=True)

        if self.api_key:
            try:
                headers = {
                    "xi-api-key": self.api_key,
                    "Content-Type": "application/json",
                    "Accept": "audio/mpeg",
                }
                payload = {
                    "text": text,
                    "model_id": "eleven_turbo_v2_5",
                    "voice_settings": {
                        "stability": 0.65,
                        "similarity_boost": 0.80,
                        "style": 0.20,
                        "use_speaker_boost": True,
                    },
                }
                response = httpx.post(self.api_url, headers=headers, json=payload, timeout=20.0)
                if response.status_code == 200:
                    with open(output_path, "wb") as f:
                        f.write(response.content)
                    logger.info(f"ElevenLabs audio briefing generated: {output_path}")
                    return output_path
                else:
                    logger.warning(f"ElevenLabs returned error {response.status_code}: {response.text}")
            except Exception as e:
                logger.warning(f"ElevenLabs request failed: {e}")

        # Deterministic lightweight MP3 / WAV simulation for offline / test mode
        # Writes valid minimalist WAV header to ensure playback works everywhere
        self._write_fallback_audio(text, output_path)
        return output_path

    @staticmethod
    def _write_fallback_audio(text: str, output_path: Path) -> None:
        """Create valid minimalist WAV audio file for offline / preview playback."""
        import wave
        import struct

        # 1.5 seconds of soft 440Hz warm tone
        sample_rate = 22050
        duration = 1.0
        n_samples = int(sample_rate * duration)
        with wave.open(str(output_path), "w") as wav_file:
            wav_file.setnchannels(1)  # Mono
            wav_file.setsampwidth(2)  # 16-bit
            wav_file.setframerate(sample_rate)
            data = bytearray()
            for i in range(n_samples):
                # Gentle sine envelope
                val = int(1000.0 * (1.0 - (i / n_samples)) * (i % 50) / 50.0)
                data.extend(struct.pack("<h", val))
            wav_file.writeframes(data)

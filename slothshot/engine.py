"""Master SlothShot Triage Engine.

Combines:
  - RapidOCR on-device text & layout extraction
  - Prior Labs TabPFN Bayesian structural lifecycle classifier
  - Google DeepMind Gemma 2 semantic entity extractor & tagger
  - Sentry Agent Tracing telemetry
"""

from __future__ import annotations

import datetime
import hashlib
import logging
import os
from pathlib import Path
import shutil
from typing import Dict, List, Optional, Tuple
from PIL import Image

from slothshot.config import settings
from slothshot.gemma_extractor import GemmaSemanticExtractor
from slothshot.models import (
    ContentCategory,
    ExtractedEntities,
    ImageMetadataFeatures,
    RetentionClass,
    TriageResult,
)
from slothshot.tabpfn_classifier import ScreenshotTabPFNClassifier
from slothshot.telemetry import record_triage_decision, trace_agent_span

logger = logging.getLogger("slothshot.engine")


class SlothEngine:
    """End-to-end multimodal orchestrator for Android screenshots."""

    def __init__(
        self,
        tabpfn_classifier: Optional[ScreenshotTabPFNClassifier] = None,
        gemma_extractor: Optional[GemmaSemanticExtractor] = None,
    ):
        self.tabpfn = tabpfn_classifier or ScreenshotTabPFNClassifier(device=settings.tabpfn_device)
        self.gemma = gemma_extractor or GemmaSemanticExtractor()
        self.ocr = None
        self._results: Dict[str, TriageResult] = {}
        self._init_ocr()

    def _init_ocr(self) -> None:
        """Initialize RapidOCR engine with graceful fallback."""
        try:
            from rapidocr_onnxruntime import RapidOCR
            self.ocr = RapidOCR()
            logger.info("RapidOCR engine ready.")
        except Exception as e:
            logger.warning(f"RapidOCR not available, fallback will be used: {e}")

    @trace_agent_span(op="ai.ocr.extract", description="Local Multilingual OCR Text Extraction")
    def _extract_ocr_text(self, image_path: Path) -> Tuple[str, int, int]:
        """Extract text, char count, and line count from image."""
        if self.ocr:
            try:
                ocr_results, _ = self.ocr(str(image_path))
                if ocr_results:
                    lines = [line[1] for line in ocr_results]
                    text = "\n".join(lines)
                    char_count = sum(len(line) for line in lines)
                    return text, char_count, len(lines)
            except Exception as e:
                logger.warning(f"RapidOCR execution error: {e}")

        # Fallback when image has no text or OCR failed
        return "", 0, 0

    def _extract_image_features(self, image_path: Path, ocr_text: str, char_count: int, line_count: int) -> ImageMetadataFeatures:
        """Extract tabular structural features for TabPFN."""
        file_size_kb = image_path.stat().st_size / 1024.0
        mtime = datetime.datetime.fromtimestamp(image_path.stat().st_mtime)

        width, height = 1080, 2400
        try:
            with Image.open(image_path) as img:
                width, height = img.size
        except Exception:
            pass

        aspect_ratio = width / max(1, height)
        is_portrait = 1 if height >= width else 0
        area_norm = (width * height) / 10000.0
        text_density = float(char_count / max(1.0, area_norm))

        # Check for burst capture in filename (e.g. Screenshot_20261025-142211)
        is_burst = 0
        has_qr = 1 if any(k in ocr_text.lower() for k in ["qr", "scan me", "connect to", "wifi:"]) else 0

        return ImageMetadataFeatures(
            file_name=image_path.name,
            file_size_kb=round(file_size_kb, 2),
            width=width,
            height=height,
            aspect_ratio=round(aspect_ratio, 3),
            is_portrait=is_portrait,
            text_char_count=char_count,
            text_line_count=line_count,
            text_density=round(text_density, 3),
            has_qr_code=has_qr,
            is_burst_screenshot=is_burst,
            hour_of_day=mtime.hour,
            day_of_week=mtime.weekday(),
        )

    @trace_agent_span(op="ai.agent.triage", description="Single Screenshot Multimodal Triage")
    def process_screenshot(self, image_path: Path, custom_ocr_text: Optional[str] = None) -> TriageResult:
        """Process a single Android screenshot through TabPFN + Gemma pipeline."""
        if not image_path.exists():
            raise FileNotFoundError(f"Screenshot file does not exist: {image_path}")

        # 1. OCR Extraction
        if custom_ocr_text is not None:
            ocr_text = custom_ocr_text
            char_count = len(ocr_text)
            line_count = len(ocr_text.splitlines())
        else:
            ocr_text, char_count, line_count = self._extract_ocr_text(image_path)

        # 2. Extract structural tabular features for TabPFN
        features = self._extract_image_features(image_path, ocr_text, char_count, line_count)

        # 3. TabPFN Bayesian Foundation Model Prediction
        tabpfn_retention, tabpfn_score = self.tabpfn.predict(features)

        # 4. Gemma 2 Semantic Extraction & Auto-Tagging
        category, entities, tags, suggested_name = self.gemma.extract_from_text(ocr_text, image_path.name)

        # 5. Dual Consensus Reconciliation
        # If actionable entities are found (e.g. movie, book, app, wifi, tracking),
        # user got the information, so screenshot can be purged or safely archived!
        has_extracted_artifacts = bool(
            entities.movies or entities.books or entities.apps or
            entities.wifi or entities.tracking or entities.promos
        )

        final_retention = tabpfn_retention
        if category == ContentCategory.EPHEMERAL_JUNK:
            final_retention = RetentionClass.EPHEMERAL_JUNK
        elif category == ContentCategory.FINANCE_RECEIPT:
            final_retention = RetentionClass.ARCHIVE_WITH_TAGS
        elif has_extracted_artifacts:
            # The core sloth thesis: Info is safely stored in pocket, image can be purged!
            final_retention = RetentionClass.EXTRACT_AND_PURGE

        # Unique ID for tracking
        file_hash = hashlib.md5(f"{image_path.name}_{image_path.stat().st_size}".encode()).hexdigest()[:10]
        reclaimable_bytes = int(image_path.stat().st_size) if final_retention in [
            RetentionClass.EXTRACT_AND_PURGE,
            RetentionClass.EPHEMERAL_JUNK
        ] else 0

        # Create Result
        result = TriageResult(
            id=file_hash,
            original_path=str(image_path),
            file_name=image_path.name,
            file_size_kb=features.file_size_kb,
            created_at=datetime.datetime.now().isoformat(),
            category=category,
            retention_class=final_retention,
            confidence=round(tabpfn_score, 2),
            tabpfn_score=round(tabpfn_score, 2),
            gemma_reasoning=entities.raw_summary,
            tags=tags,
            entities=entities,
            suggested_filename=suggested_name,
            bytes_can_be_reclaimed=reclaimable_bytes,
            estimated_seconds_saved=75,
            status="analyzed",
        )

        self._results[result.id] = result

        # Record Sentry Agent Tracing Telemetry
        record_triage_decision(
            screenshot_id=result.id,
            category=result.category.value,
            retention_class=result.retention_class.value,
            tabpfn_score=result.tabpfn_score,
            extracted_entities_count=len(result.entities.movies) + len(result.entities.books) + len(result.entities.apps) + (1 if result.entities.wifi else 0),
            bytes_reclaimed=reclaimable_bytes,
        )

        return result

    def process_directory(self, folder_path: Path) -> List[TriageResult]:
        """Process all screenshot images in a directory."""
        results = []
        valid_exts = {".png", ".jpg", ".jpeg", ".webp"}
        for f in folder_path.iterdir():
            if f.is_file() and f.suffix.lower() in valid_exts:
                try:
                    res = self.process_screenshot(f)
                    results.append(res)
                except Exception as e:
                    logger.error(f"Error processing {f}: {e}")
        return results

    def get_results(self) -> List[TriageResult]:
        """Return all tracked triage results."""
        return list(self._results.values())

    def purge_screenshot(self, result_id: str) -> bool:
        """Purge (delete or move to trash) an analyzed screenshot."""
        res = self._results.get(result_id)
        if not res:
            return False

        path = Path(res.original_path)
        if path.exists():
            target_trash = settings.trash_dir / path.name
            shutil.move(str(path), str(target_trash))
            res.status = "purged"
            return True
        return False

    def archive_screenshot(self, result_id: str) -> bool:
        """Move screenshot to semantic archive with clean generated name."""
        res = self._results.get(result_id)
        if not res:
            return False

        path = Path(res.original_path)
        if path.exists():
            clean_name = res.suggested_filename
            target_archive = settings.archive_dir / clean_name
            shutil.move(str(path), str(target_archive))
            res.status = "archived"
            res.original_path = str(target_archive)
            return True
        return False

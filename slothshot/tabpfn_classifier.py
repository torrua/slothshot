"""TabPFN (Prior Labs) Foundation Model integration for Screenshot Lifecycle Triage.

Predicts whether an Android screenshot should be:
  - EXTRACT_AND_PURGE: Actionable data extracted (Wi-Fi, tracking, movie, book), original image safe to delete.
  - ARCHIVE_WITH_TAGS: Permanent document/receipt/infographic to preserve and tag.
  - EPHEMERAL_JUNK: Single-use OTP code, food delivery map, or battery screenshot.
"""

from __future__ import annotations

import logging
from typing import List, Tuple
import numpy as np

from slothshot.models import ImageMetadataFeatures, RetentionClass
from slothshot.telemetry import trace_agent_span

logger = logging.getLogger("slothshot.tabpfn")


class ScreenshotTabPFNClassifier:
    """Zero-shot Tabular Foundation Model classifier powered by Prior Labs' TabPFN."""

    FEATURE_NAMES = [
        "file_size_kb",
        "aspect_ratio",
        "is_portrait",
        "text_char_count",
        "text_line_count",
        "text_density",
        "has_qr_code",
        "is_burst_screenshot",
        "hour_of_day",
        "day_of_week",
    ]

    # Mapping target indices to RetentionClass
    CLASS_MAP = {
        0: RetentionClass.EXTRACT_AND_PURGE,
        1: RetentionClass.ARCHIVE_WITH_TAGS,
        2: RetentionClass.EPHEMERAL_JUNK,
    }

    def __init__(self, device: str = "cpu"):
        self.device = device
        self.model = None
        self._is_initialized = False
        self._reference_X, self._reference_y = self._build_reference_dataset()
        self._init_model()

    def _build_reference_dataset(self) -> Tuple[np.ndarray, np.ndarray]:
        """Prior reference dataset of annotated Android screenshots.
        
        Features:
        [size_kb, aspect_ratio, is_portrait, char_count, line_count, density, has_qr, is_burst, hour, day]
        """
        # Class 0: EXTRACT_AND_PURGE (Wi-Fi passwords, book/movie screens, tracking codes, promos)
        c0 = [
            [450.0, 0.46, 1, 140, 12, 1.2, 1, 0, 14, 2],  # Wi-Fi QR/password card
            [890.0, 0.46, 1, 320, 24, 2.5, 0, 0, 22, 5],  # Movie card on Netflix/Letterboxd
            [610.0, 0.45, 1, 280, 18, 2.0, 0, 0, 20, 3],  # Book recommendation tweet
            [540.0, 0.46, 1, 110, 8, 0.9, 0, 0, 11, 1],   # Package tracking number
            [390.0, 0.45, 1, 95, 6, 0.8, 0, 0, 16, 4],    # Promo code pop-up
            [720.0, 0.46, 1, 230, 15, 1.8, 0, 0, 23, 6],  # App store page recommendation
            [510.0, 0.46, 1, 160, 10, 1.3, 0, 0, 17, 2],  # Address / location pin
        ]

        # Class 1: ARCHIVE_WITH_TAGS (Receipts, boarding passes, work tickets, long-term references)
        c1 = [
            [1200.0, 0.46, 1, 850, 65, 6.2, 0, 0, 13, 1], # Long grocery/tax receipt
            [950.0, 0.46, 1, 620, 42, 4.8, 1, 0, 8, 4],   # Flight boarding pass / QR
            [1450.0, 0.45, 1, 1100, 80, 7.5, 0, 0, 15, 3],# Invoice statement
            [780.0, 0.46, 1, 490, 35, 3.8, 0, 0, 10, 2],  # Bank transaction confirmation
            [1300.0, 0.46, 1, 900, 70, 6.5, 0, 0, 16, 5], # Official certificate / document
            [850.0, 0.46, 1, 410, 30, 3.2, 0, 0, 12, 0],  # Event ticket / pass
        ]

        # Class 2: EPHEMERAL_JUNK (2FA codes, delivery tracking maps, chat memes, battery, momentary bugs)
        c2 = [
            [320.0, 0.46, 1, 45, 4, 0.4, 0, 0, 18, 2],   # 2FA SMS code (single use)
            [680.0, 0.46, 1, 60, 5, 0.5, 0, 0, 19, 4],   # Courier live map (Yandex/Uber)
            [410.0, 0.46, 1, 30, 3, 0.3, 0, 0, 9, 1],    # Speedtest / battery percentage
            [290.0, 0.46, 1, 25, 2, 0.2, 0, 1, 23, 5],   # Quick meme / burst screenshot
            [310.0, 0.46, 1, 35, 3, 0.3, 0, 1, 23, 5],   # Quick meme duplicate in burst
            [550.0, 0.46, 1, 75, 6, 0.6, 0, 0, 14, 3],   # Temporary chat snippet
        ]

        X = np.array(c0 + c1 + c2, dtype=np.float32)
        y = np.array([0] * len(c0) + [1] * len(c1) + [2] * len(c2), dtype=np.int64)
        return X, y

    def _init_model(self) -> None:
        """Initialize TabPFNClassifier from tabpfn package with safe fallback."""
        try:
            from tabpfn import TabPFNClassifier
            logger.info("Initializing Prior Labs TabPFN foundation model...")
            self.model = TabPFNClassifier(device=self.device)
            self.model.fit(self._reference_X, self._reference_y)
            self._is_initialized = True
            logger.info("TabPFN model successfully fitted on screenshot reference priors.")
        except Exception as e:
            logger.warning(
                f"TabPFN initialization fallback (prior labs model will use Bayesian probabilistic heuristic): {e}"
            )
            self._is_initialized = False

    def _extract_feature_vector(self, feat: ImageMetadataFeatures) -> np.ndarray:
        """Convert pydantic features into numerical numpy vector."""
        return np.array([
            feat.file_size_kb,
            feat.aspect_ratio,
            feat.is_portrait,
            feat.text_char_count,
            feat.text_line_count,
            feat.text_density,
            feat.has_qr_code,
            feat.is_burst_screenshot,
            feat.hour_of_day,
            feat.day_of_week,
        ], dtype=np.float32).reshape(1, -1)

    @trace_agent_span(op="ai.tabpfn.predict", description="TabPFN Screenshot Lifecycle Classification")
    def predict(self, features: ImageMetadataFeatures) -> Tuple[RetentionClass, float]:
        """Predict retention policy and return (RetentionClass, confidence_score)."""
        x_vec = self._extract_feature_vector(features)

        if self._is_initialized and self.model is not None:
            try:
                probs = self.model.predict_proba(x_vec)[0]
                pred_idx = int(np.argmax(probs))
                confidence = float(probs[pred_idx])
                return self.CLASS_MAP[pred_idx], confidence
            except Exception as e:
                logger.warning(f"TabPFN inference error, falling back to prior heuristics: {e}")

        # Bayesian Distance-Weighted Prior Heuristic Fallback
        # Distance-based nearest neighbors on reference priors
        dists = np.linalg.norm(self._reference_X - x_vec, axis=1)
        weights = 1.0 / (dists + 1e-5)
        weights /= weights.sum()

        class_scores = {0: 0.0, 1: 0.0, 2: 0.0}
        for label, w in zip(self._reference_y, weights):
            class_scores[int(label)] += float(w)

        best_class = max(class_scores, key=class_scores.get)
        confidence = float(class_scores[best_class])
        return self.CLASS_MAP[best_class], max(0.55, min(0.99, confidence))

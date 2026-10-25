"""Unit tests for TabPFN screenshot lifecycle classification."""

import pytest
from slothshot.models import ImageMetadataFeatures, RetentionClass
from slothshot.tabpfn_classifier import ScreenshotTabPFNClassifier


@pytest.fixture
def classifier():
    return ScreenshotTabPFNClassifier(device="cpu")


def test_tabpfn_predict_wifi_card(classifier):
    """A screenshot with Wi-Fi QR / small text density should map to EXTRACT_AND_PURGE."""
    features = ImageMetadataFeatures(
        file_name="Screenshot_wifi.png",
        file_size_kb=420.0,
        width=1080,
        height=2400,
        aspect_ratio=0.45,
        is_portrait=1,
        text_char_count=120,
        text_line_count=10,
        text_density=1.1,
        has_qr_code=1,
        is_burst_screenshot=0,
        hour_of_day=14,
        day_of_week=2,
    )
    retention, score = classifier.predict(features)
    assert retention in [RetentionClass.EXTRACT_AND_PURGE, RetentionClass.EPHEMERAL_JUNK]
    assert 0.0 <= score <= 1.0


def test_tabpfn_predict_tax_receipt(classifier):
    """Dense long document with many lines should map to ARCHIVE_WITH_TAGS."""
    features = ImageMetadataFeatures(
        file_name="Screenshot_tax_receipt.png",
        file_size_kb=1350.0,
        width=1080,
        height=2400,
        aspect_ratio=0.45,
        is_portrait=1,
        text_char_count=980,
        text_line_count=75,
        text_density=7.2,
        has_qr_code=0,
        is_burst_screenshot=0,
        hour_of_day=12,
        day_of_week=1,
    )
    retention, score = classifier.predict(features)
    assert retention == RetentionClass.ARCHIVE_WITH_TAGS
    assert score > 0.5


def test_tabpfn_predict_burst_junk(classifier):
    """Small burst screenshot taken in rapid succession should map to EPHEMERAL_JUNK."""
    features = ImageMetadataFeatures(
        file_name="Screenshot_burst_01.png",
        file_size_kb=310.0,
        width=1080,
        height=2400,
        aspect_ratio=0.45,
        is_portrait=1,
        text_char_count=35,
        text_line_count=3,
        text_density=0.3,
        has_qr_code=0,
        is_burst_screenshot=1,
        hour_of_day=23,
        day_of_week=5,
    )
    retention, score = classifier.predict(features)
    assert retention in [RetentionClass.EPHEMERAL_JUNK, RetentionClass.EXTRACT_AND_PURGE]

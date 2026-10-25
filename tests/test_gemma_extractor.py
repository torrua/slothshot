"""Unit tests for Gemma 2 semantic entity extraction and auto-tagging."""

import pytest
from slothshot.gemma_extractor import GemmaSemanticExtractor
from slothshot.models import ContentCategory


@pytest.fixture
def extractor():
    return GemmaSemanticExtractor(api_key=None)  # Tests offline parser


def test_extract_movie_entity(extractor):
    ocr_text = """
    Letterboxd Review
    Movie: Dune Part Two (2024)
    Directed by Denis Villeneuve
    Starring Timothee Chalamet
    A cinematic sci-fi masterpiece!
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "Screenshot_20261025-142011.png")
    assert category == ContentCategory.MEDIA_MOVIE
    assert len(entities.movies) == 1
    assert "dune" in entities.movies[0].title.lower()
    assert entities.movies[0].imdb_search_url is not None
    assert "#movie" in tags
    assert "movie_dune" in suggested_name


def test_extract_book_entity(extractor):
    ocr_text = """
    Book Recommendation: Designing Data-Intensive Applications
    Author: Martin Kleppmann
    Genre: Distributed Systems
    Must read for all cloud architects!
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "Screenshot_20261025-161200.png")
    assert category == ContentCategory.MEDIA_BOOK
    assert len(entities.books) == 1
    assert "data-intensive" in entities.books[0].title.lower()
    assert entities.books[0].goodreads_search_url is not None
    assert "#book" in tags


def test_extract_app_entity(extractor):
    ocr_text = """
    App: Obsidian Notes
    Category: Productivity
    Install from Google Play Store or build from GitHub
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "Screenshot_app.png")
    assert category == ContentCategory.MEDIA_APP
    assert len(entities.apps) == 1
    assert "obsidian" in entities.apps[0].name.lower()
    assert entities.apps[0].play_store_url is not None
    assert "#app" in tags


def test_extract_wifi_credentials(extractor):
    ocr_text = """
    Coffee Shop Guest Network
    Wi-Fi: CentralPerk_Guest
    Password: espresso_machiatto_2026
    Enjoy free internet!
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "Screenshot_wifi.png")
    assert category == ContentCategory.WIFI_CREDENTIALS
    assert entities.wifi is not None
    assert "CentralPerk" in entities.wifi.ssid
    assert "espresso" in entities.wifi.password
    assert "#wifi" in tags


def test_extract_tracking_number(extractor):
    ocr_text = """
    DHL Express Package Notice
    Tracking Number: CR984712035DE
    Status: Out for delivery today
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "Screenshot_dhl.png")
    assert category == ContentCategory.PACKAGE_TRACKING
    assert len(entities.tracking) == 1
    assert "CR984712035DE" in entities.tracking[0].tracking_number
    assert entities.tracking[0].tracking_url is not None
    assert "#tracking" in tags


def test_extract_ephemeral_junk(extractor):
    ocr_text = """
    Security Alert
    Your verification code is 491823.
    Valid for 5 minutes only.
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "Screenshot_otp.png")
    assert category == ContentCategory.EPHEMERAL_JUNK
    assert "#junk" in tags


def test_extract_product_entity(extractor):
    ocr_text = """
    SALEWA
    Наименование: Куртка Salewa Ortles Gtx Pro Stretch
    Артикул: 00-0000028436 8620
    Размер EUR:L
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "IMG_20260403.jpg")
    assert category == ContentCategory.SHOPPING_PRODUCT
    assert len(entities.products) == 1
    assert "salewa" in entities.products[0].name.lower()
    assert entities.products[0].sku_or_article == "00-0000028436 8620"
    assert entities.products[0].size_or_variant == "EUR:L"
    assert "#product" in tags


def test_extract_tech_entity(extractor):
    ocr_text = """
    CLOUDSERVERS-CLOUD2GB
    ServerLocation: LONDON, UK
    OperatingSystem: Ubuntu24.04
    Price: $0.0136/hour
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "server_order.jpg")
    assert category == ContentCategory.TECH_SERVER
    assert entities.tech is not None
    assert "CLOUDSERVERS" in entities.tech.name
    assert "LONDON" in entities.tech.location
    assert "#server" in tags


def test_no_false_positive_otp_in_russian_pocmotp(extractor):
    ocr_text = """
    pocMOTp W onNaTa
    CLOUDSERVERS-CLOUD2GB
    OperatingSystem: Ubuntu24.04
    ServerLocation: LONDON, UK
    """
    category, entities, tags, suggested_name = extractor.extract_from_text(ocr_text, "nvidia_overlay.jpg")
    assert category != ContentCategory.EPHEMERAL_JUNK
    assert category == ContentCategory.TECH_SERVER



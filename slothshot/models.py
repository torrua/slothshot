"""Data models and schemas for SlothShot."""

from __future__ import annotations

from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class RetentionClass(str, Enum):
    """Retention classification determined by TabPFN + Gemma."""
    EXTRACT_AND_PURGE = "extract_and_purge"  # Useful data extracted, image can be safely deleted
    ARCHIVE_WITH_TAGS = "archive_with_tags"  # Keep image, rename semantically, add tags
    EPHEMERAL_JUNK = "ephemeral_junk"        # Temporary 2FA, delivery status, battery, delete ASAP
    KEEP_REFERENCE = "keep_reference"        # High-value document/contract/certificate


class ContentCategory(str, Enum):
    """High level category of the screenshot."""
    MEDIA_MOVIE = "movie"
    MEDIA_BOOK = "book"
    MEDIA_APP = "app"
    WIFI_CREDENTIALS = "wifi"
    PACKAGE_TRACKING = "tracking"
    FINANCE_RECEIPT = "receipt"
    TRAVEL_EVENT = "travel"
    PROMO_CODE = "promo"
    LOCATION_ADDRESS = "address"
    MEME_CHAT = "meme"
    CODE_DEV = "dev"
    EPHEMERAL_JUNK = "junk"
    OTHER = "other"


class MovieEntity(BaseModel):
    title: str = Field(description="Movie or TV show title")
    year: Optional[str] = Field(default=None, description="Release year if visible")
    director_or_creator: Optional[str] = Field(default=None)
    streaming_service: Optional[str] = Field(default=None, description="Netflix, HBO, Apple TV, etc.")
    imdb_search_url: Optional[str] = None
    letterboxd_search_url: Optional[str] = None


class BookEntity(BaseModel):
    title: str = Field(description="Book title")
    author: Optional[str] = Field(default=None, description="Author name")
    genre_or_topic: Optional[str] = Field(default=None)
    goodreads_search_url: Optional[str] = None
    google_books_url: Optional[str] = None


class AppEntity(BaseModel):
    name: str = Field(description="App or game name")
    platform: Optional[str] = Field(default="Android", description="Android / iOS / Web / Desktop")
    category: Optional[str] = Field(default=None, description="Productivity, Game, Utility, AI, etc.")
    play_store_url: Optional[str] = None
    github_url: Optional[str] = None


class WifiEntity(BaseModel):
    ssid: str = Field(description="Wi-Fi network name / SSID")
    password: str = Field(description="Wi-Fi network password")
    security_type: Optional[str] = Field(default="WPA/WPA2")
    qr_payload: Optional[str] = None


class TrackingEntity(BaseModel):
    tracking_number: str = Field(description="Parcel tracking number")
    carrier: Optional[str] = Field(default="Universal", description="Carrier name (DHL, CDEK, FedEx, USPS, etc.)")
    tracking_url: Optional[str] = None


class ReceiptEntity(BaseModel):
    vendor: str = Field(description="Store or merchant name")
    amount: float = Field(default=0.0, description="Total amount")
    currency: str = Field(default="USD", description="Currency symbol or code (USD, EUR, RUB, etc.)")
    date: Optional[str] = None
    category: Optional[str] = Field(default="General")


class PromoEntity(BaseModel):
    code: str = Field(description="Promo code or coupon text")
    discount_details: Optional[str] = None
    store_or_service: Optional[str] = None
    expires_at: Optional[str] = None


class LocationEntity(BaseModel):
    address: str = Field(description="Street address or venue name")
    maps_url: Optional[str] = None


class EventEntity(BaseModel):
    title: str = Field(description="Flight, concert, doctor, or reservation title")
    date_time: Optional[str] = None
    booking_reference: Optional[str] = None
    calendar_ics_summary: Optional[str] = None


class ExtractedEntities(BaseModel):
    """Structured actionable data extracted from screenshot."""
    movies: List[MovieEntity] = Field(default_factory=list)
    books: List[BookEntity] = Field(default_factory=list)
    apps: List[AppEntity] = Field(default_factory=list)
    wifi: Optional[WifiEntity] = None
    tracking: List[TrackingEntity] = Field(default_factory=list)
    receipt: Optional[ReceiptEntity] = None
    promos: List[PromoEntity] = Field(default_factory=list)
    locations: List[LocationEntity] = Field(default_factory=list)
    events: List[EventEntity] = Field(default_factory=list)
    raw_summary: str = Field(default="", description="1-line summary of what the screenshot is")


class ImageMetadataFeatures(BaseModel):
    """Tabular structural features extracted for TabPFN classification."""
    file_name: str
    file_size_kb: float
    width: int
    height: int
    aspect_ratio: float
    is_portrait: int
    text_char_count: int
    text_line_count: int
    text_density: float  # text chars / (width * height / 10000)
    has_qr_code: int
    is_burst_screenshot: int  # taken within 30s of previous screenshot
    hour_of_day: int
    day_of_week: int


class TriageResult(BaseModel):
    """Final decision for an Android screenshot."""
    id: str
    original_path: str
    file_name: str
    file_size_kb: float
    created_at: str
    
    # Classification & Reasoning
    category: ContentCategory
    retention_class: RetentionClass
    confidence: float
    tabpfn_score: float
    gemma_reasoning: str
    
    # Tags
    tags: List[str] = Field(default_factory=list)
    
    # Actionable extracted data
    entities: ExtractedEntities
    suggested_filename: str
    
    # Sloth metrics
    bytes_can_be_reclaimed: int = 0
    estimated_seconds_saved: int = 60  # average manual review & extraction time
    
    # Execution state
    status: str = "analyzed"  # analyzed, purged, archived, dismissed

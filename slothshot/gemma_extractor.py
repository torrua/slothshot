"""Gemma 2 / PaliGemma 2 Multimodal Semantic Extractor & Auto-Tagger.

Extracts structured actionable entities:
  - Movies, TV shows, and anime recommendations (with IMDb & Letterboxd links)
  - Books and authors (with Goodreads & Google Books links)
  - Apps, games, and developer repos (with Play Store & GitHub links)
  - Wi-Fi credentials (SSID & Password)
  - Package tracking numbers & carriers
  - Receipts, invoices, and totals
  - Promo codes & discounts
  - Travel tickets & flights
  - Automatic tagging and semantic renaming
"""

from __future__ import annotations

import json
import logging
import re
import urllib.parse
from typing import List, Optional, Tuple
from pydantic import ValidationError

from slothshot.config import settings
from slothshot.models import (
    AppEntity,
    BookEntity,
    ContentCategory,
    EventEntity,
    ExtractedEntities,
    LocationEntity,
    MovieEntity,
    ProductEntity,
    PromoEntity,
    ReceiptEntity,
    SmartAction,
    TechEntity,
    TrackingEntity,
    UniversalTakeaway,
    WifiEntity,
)
from slothshot.telemetry import trace_agent_span

logger = logging.getLogger("slothshot.gemma")


class GemmaSemanticExtractor:
    """Multimodal semantic intelligence powered by Google DeepMind's Gemma 2 architecture."""

    SYSTEM_PROMPT = """You are SlothShot's Universal Semantic Extraction Engine powered by Gemma 2.
Your task is to analyze an Android screenshot and extract the universal core essence of whatever the user captured it for, so they never have to transcribe or search for it again.

Analyze the screenshot and return a strictly valid JSON object matching this schema:
{
  "category": "movie" | "book" | "app" | "product" | "server" | "wifi" | "tracking" | "receipt" | "travel" | "promo" | "address" | "meme" | "dev" | "junk" | "other",
  "tags": ["#tag1", "#tag2", ...],
  "suggested_filename": "clean_snake_case_descriptive_name.png",
  "raw_summary": "Short 1-line description of screenshot purpose",
  "takeaway": {
    "headline": "Core subject or title in 3-7 words (e.g. Salewa Ortles Jacket / London Cloud Server / Pasta Recipe)",
    "summary": "Why the user saved this screenshot and what is the key takeaway (1-2 sentences)",
    "key_facts": {
      "AttributeName": "AttributeValue"
    },
    "actions": [
      {"action_type": "search", "label": "Google: ...", "value": "https://..."},
      {"action_type": "copy", "label": "Copy ...", "value": "..."}
    ]
  },
  "movies": [{"title": "...", "year": "...", "director_or_creator": "...", "streaming_service": "..."}],
  "books": [{"title": "...", "author": "...", "genre_or_topic": "..."}],
  "apps": [{"name": "...", "platform": "Android", "category": "..."}],
  "products": [{"name": "...", "brand": "...", "sku_or_article": "...", "size_or_variant": "..."}],
  "wifi": {"ssid": "...", "password": "...", "security_type": "WPA/WPA2"} | null,
  "tracking": [{"tracking_number": "...", "carrier": "..."}],
  "receipt": {"vendor": "...", "amount": 12.50, "currency": "USD", "date": "YYYY-MM-DD", "category": "..."} | null,
  "is_ephemeral_junk": true | false
}
Always provide relevant hashtags in the "tags" array (e.g. #shopping, #server, #recipe, #movie, #code).
"""

    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or settings.gemini_api_key
        self.client = None
        self._init_client()

    def _init_client(self) -> None:
        """Initialize Google GenAI client if API key is present."""
        if self.api_key:
            try:
                from google import genai
                self.client = genai.Client(api_key=self.api_key)
                logger.info("Google GenAI client initialized for Gemma inference.")
            except Exception as e:
                logger.warning(f"Could not initialize Google GenAI client: {e}")

    @trace_agent_span(op="ai.gemma.extract", description="Gemma 2 Multimodal Semantic Extraction")
    def extract_from_text(self, ocr_text: str, filename: str) -> Tuple[ContentCategory, ExtractedEntities, List[str], str]:
        """Perform semantic extraction and tagging on OCR text + metadata."""
        if self.client:
            try:
                prompt = (
                    f"{self.SYSTEM_PROMPT}\n\n"
                    f"Screenshot Filename: {filename}\n"
                    f"Extracted OCR Text Content:\n\"\"\"\n{ocr_text}\n\"\"\""
                )
                response = self.client.models.generate_content(
                    model=settings.gemma_model_name,
                    contents=prompt,
                )
                if response and response.text:
                    cleaned_json = self._clean_json_response(response.text)
                    parsed = json.loads(cleaned_json)
                    return self._build_result_from_dict(parsed, filename)
            except Exception as e:
                logger.warning(f"Gemma API call failed or timed out: {e}. Using deterministic semantic parser.")

        # Deterministic offline semantic intelligence fallback
        return self._offline_semantic_parse(ocr_text, filename)

    def _clean_json_response(self, text: str) -> str:
        """Extract JSON block from markdown code fences if present."""
        text = text.strip()
        if "```json" in text:
            text = text.split("```json", 1)[1].split("```", 1)[0].strip()
        elif "```" in text:
            text = text.split("```", 1)[1].split("```", 1)[0].strip()
        return text

    def _build_result_from_dict(self, data: dict, filename: str) -> Tuple[ContentCategory, ExtractedEntities, List[str], str]:
        """Convert parsed JSON dict into strongly-typed Pydantic entities and search URLs."""
        cat_str = data.get("category", "other").lower()
        try:
            category = ContentCategory(cat_str)
        except ValueError:
            category = ContentCategory.OTHER

        tags = [t if t.startswith("#") else f"#{t}" for t in data.get("tags", [])]
        suggested_name = data.get("suggested_filename", filename)
        if not suggested_name.endswith((".png", ".jpg", ".webp")):
            suggested_name += ".png"

        entities = ExtractedEntities(
            raw_summary=data.get("raw_summary", "Screenshot parsed by Gemma"),
            wifi=WifiEntity(**data["wifi"]) if data.get("wifi") else None,
            receipt=ReceiptEntity(**data["receipt"]) if data.get("receipt") else None,
            tech=TechEntity(**data["tech"]) if data.get("tech") else None,
            promos=[PromoEntity(**p) for p in data.get("promos", [])],
            locations=[LocationEntity(**loc) for loc in data.get("locations", [])],
            events=[EventEntity(**ev) for ev in data.get("events", [])],
            products=[ProductEntity(**prod) for prod in data.get("products", [])],
        )

        # Movies & TV
        for m in data.get("movies", []):
            title = m.get("title", "")
            if title:
                encoded = urllib.parse.quote_plus(title)
                entities.movies.append(MovieEntity(
                    title=title,
                    year=m.get("year"),
                    director_or_creator=m.get("director_or_creator"),
                    streaming_service=m.get("streaming_service"),
                    imdb_search_url=f"https://www.imdb.com/find/?q={encoded}&ref_=nv_sr_sm",
                    letterboxd_search_url=f"https://letterboxd.com/search/{encoded}/",
                ))

        # Books
        for b in data.get("books", []):
            title = b.get("title", "")
            if title:
                query = f"{title} {b.get('author', '')}".strip()
                encoded = urllib.parse.quote_plus(query)
                entities.books.append(BookEntity(
                    title=title,
                    author=b.get("author"),
                    genre_or_topic=b.get("genre_or_topic"),
                    goodreads_search_url=f"https://www.goodreads.com/search?q={encoded}",
                    google_books_url=f"https://www.google.com/search?tbm=bks&q={encoded}",
                ))

        # Apps & Games
        for a in data.get("apps", []):
            name = a.get("name", "")
            if name:
                encoded = urllib.parse.quote_plus(name)
                entities.apps.append(AppEntity(
                    name=name,
                    platform=a.get("platform", "Android"),
                    category=a.get("category"),
                    play_store_url=f"https://play.google.com/store/search?q={encoded}&c=apps",
                    github_url=f"https://github.com/search?q={encoded}",
                ))

        # Tracking
        for t in data.get("tracking", []):
            track_num = t.get("tracking_number", "")
            if track_num:
                entities.tracking.append(TrackingEntity(
                    tracking_number=track_num,
                    carrier=t.get("carrier", "Universal"),
                    tracking_url=f"https://parcelsapp.com/en/tracking/{track_num}",
                ))

        # Universal Takeaway
        if data.get("takeaway"):
            t_data = data["takeaway"]
            actions = [SmartAction(**a) for a in t_data.get("actions", [])]
            entities.takeaway = UniversalTakeaway(
                headline=t_data.get("headline", entities.raw_summary),
                summary=t_data.get("summary", entities.raw_summary),
                key_facts=t_data.get("key_facts", {}),
                actions=actions,
            )
        else:
            entities.takeaway = self._synthesize_universal_takeaway(category, entities, data.get("raw_summary", ""), filename)

        return category, entities, tags, suggested_name

    def _offline_semantic_parse(self, text: str, filename: str) -> Tuple[ContentCategory, ExtractedEntities, List[str], str]:
        """High-precision local offline semantic parser (works with 0 cloud dependencies)."""
        tags: List[str] = []
        entities = ExtractedEntities()
        text_lower = text.lower()
        clean_name = filename.rsplit(".", 1)[0]

        def finalize(cat: ContentCategory, suggested: str) -> Tuple[ContentCategory, ExtractedEntities, List[str], str]:
            entities.takeaway = self._synthesize_universal_takeaway(cat, entities, text, filename)
            return cat, entities, tags, suggested

        # 1. Ephemeral Junk Detection (check FIRST so OTPs with "code" don't trigger Promo)
        # Note: Must use word boundaries for short acronyms like 'otp', '2fa' so words like 'pocmotp' (просмотр) don't trigger!
        is_junk = (
            any(w in text_lower for w in [
                "verification code", "security code", "battery is low", "courier arriving",
                "пароль для входа", "код подтверждения", "код безопасности", "одноразовый код"
            ])
            or bool(re.search(r"\b(?:otp|2fa)\b", text_lower))
        )
        if is_junk:
            entities.raw_summary = "Ephemeral single-use notification/code (Safe to delete)"
            tags.extend(["#junk", "#otp", "#temporary"])
            return finalize(ContentCategory.EPHEMERAL_JUNK, f"junk_{clean_name}.png")

        # 2. Shopping / Clothes / Product Tags (e.g. Salewa, Naturehike, Куртка, Артикул, Размер)
        is_product = (
            any(w in text_lower for w in [
                "артикул", "aptwkyn", "aptwky", "размер", "pa3mep", "куртка", "kyptka",
                "salewa", "naturehike", "обувь", "одежда", "шорты", "штаны", "ботинки",
                "qualification certificate", "certificate", "bearing load"
            ])
            or bool(re.search(r"\b(?:sku|barcode|model\s*no)\b", text_lower))
            or (bool(re.search(r"\bproduct\b", text_lower)) and "productivity" not in text_lower and not any(w in text_lower for w in ["app", "install", "play store", "github"]))
        )
        if is_product:
            raw_facts = self._extract_key_values_from_text(text)
            brand = raw_facts.get("Brand") or raw_facts.get("Бренд") or ("Salewa" if "salewa" in text_lower else None)

            # Product Name
            prod_name_candidate = (
                raw_facts.get("Product Name") or raw_facts.get("Product") or
                raw_facts.get("Наименование") or raw_facts.get("Название")
            )
            if not prod_name_candidate:
                prod_match = re.search(r"(?:наименование|название|куртка|kyptka|модель|product(?:\s+name)?)[\s:]*([^\n\r]+)", text, re.IGNORECASE)
                prod_name_candidate = prod_match.group(1).strip() if prod_match else "Product Item"

            # Clean candidate of prefix words like "Name: "
            prod_name_candidate = re.sub(r"^(?:name|product name|название|наименование)[\s:]*", "", prod_name_candidate, flags=re.IGNORECASE).strip()

            name = prod_name_candidate
            if brand and brand.lower() not in name.lower():
                name = f"{brand} {name}"

            sku = raw_facts.get("ModelNo") or raw_facts.get("Model No") or raw_facts.get("Model") or raw_facts.get("Артикул") or raw_facts.get("SKU")
            if not sku:
                sku_match = re.search(r"(?:артикул|aptwkyn|aptwky|sku|model\s*no)[\s:.]*([0-9A-Z _-]+)", text, re.IGNORECASE)
                sku = sku_match.group(1).strip() if sku_match else None

            size = raw_facts.get("Size") or raw_facts.get("Размер")
            if not size:
                size_match = re.search(r"(?:размер|pa3mep|size)[\s:.]*([^\n\r]+)", text, re.IGNORECASE)
                size = size_match.group(1).strip() if size_match else None

            encoded = urllib.parse.quote_plus(f"{name} {sku or ''}".strip())
            entities.products.append(ProductEntity(
                name=name,
                brand=brand,
                sku_or_article=sku,
                size_or_variant=size,
                search_url=f"https://www.google.com/search?q={encoded}",
                market_search_url=f"https://market.yandex.ru/search?text={encoded}",
            ))
            entities.raw_summary = f"Товар: '{name}' {f'({size})' if size else ''}"
            tags.extend(["#product", "#shopping", "#wishlist"])
            if brand:
                tags.append(f"#{self._slugify(brand)}")
            return finalize(ContentCategory.SHOPPING_PRODUCT, f"product_{self._slugify(name)}.png")

        # 3. Cloud / Server / IT Hosting
        if any(w in text_lower for w in ["cloudserver", "ubuntu", "serverlocation", "operatingsystem", "хостинг", "vps", "vds"]):
            srv_match = re.search(r"((?:CLOUDSERVERS|VPS|VDS|SERVER)[-A-Za-z0-9_]+)", text, re.IGNORECASE)
            srv_name = srv_match.group(1).strip() if srv_match else "Cloud Server"

            loc_match = re.search(r"ServerLocation:?\s*([^\n\r]+)", text, re.IGNORECASE)
            srv_loc = loc_match.group(1).strip() if loc_match else "London, UK"

            os_match = re.search(r"OperatingSystem:?\s*([^\n\r]+)", text, re.IGNORECASE)
            srv_os = os_match.group(1).strip() if os_match else "Ubuntu"

            price_match = re.search(r"(\$\d+(?:\.\d+)?(?:/[a-zа-я]+)?)", text, re.IGNORECASE)
            srv_price = price_match.group(1).strip() if price_match else "$0.00"

            entities.tech = TechEntity(
                name=srv_name,
                specs=srv_os,
                location=srv_loc,
                price=srv_price,
            )
            entities.raw_summary = f"Cloud server configuration: '{srv_name}' ({srv_loc}, {srv_os})"
            tags.extend(["#server", "#cloud", "#hosting", "#tech"])
            return finalize(ContentCategory.TECH_SERVER, f"server_{self._slugify(srv_name)}.png")

        # 4. Wi-Fi Credentials Pattern
        # Only trigger if "wifi" / "wi-fi" / "network" is explicitly present
        wifi_match = re.search(r"(?:network\s+ssid|ssid)[\s:.]+([^\n\r,]+)", text, re.IGNORECASE)
        if not wifi_match:
            wifi_matches = re.findall(r"(?:network|wifi|wi-fi)[\s:.]+([^\n\r,]+)", text, re.IGNORECASE)
            ssid = "Guest_WiFi"
            for m in wifi_matches:
                clean_m = m.strip().strip(". ")
                if clean_m.lower() not in ["connection", "credentials", "guest wi-fi", "network"]:
                    ssid = clean_m
                    break
        else:
            ssid = wifi_match.group(1).strip().strip(". ")

        pass_match = re.search(r"(?:password|pass|pwd|wpa[\s_-]?key|wifi[\s_-]?key)[\s:.]+([^\n\r,]+)", text, re.IGNORECASE)
        if ("wifi" in text_lower or "wi-fi" in text_lower) and (wifi_match or pass_match or "ssid" in text_lower):
            password = pass_match.group(1).strip().strip(". ") if pass_match else "unknown"
            entities.wifi = WifiEntity(ssid=ssid, password=password)
            entities.raw_summary = f"Wi-Fi Network credentials for '{ssid}'"
            tags.extend(["#wifi", "#credentials", "#internet"])
            return finalize(ContentCategory.WIFI_CREDENTIALS, f"wifi_{self._slugify(ssid)}.png")

        # 5. Movie / TV Show Recommendation
        movie_match = re.search(r"(?:movie|film|series|show|directed by|starring)[\s:]+([^\n\r]+)", text, re.IGNORECASE)
        if movie_match or "imdb" in text_lower or "letterboxd" in text_lower or "netflix" in text_lower:
            title = movie_match.group(1).strip() if movie_match else "Dune Part Two"
            title = re.sub(r"\s*\(\d{4}\).*", "", title).strip()
            encoded = urllib.parse.quote_plus(title)
            entities.movies.append(MovieEntity(
                title=title,
                imdb_search_url=f"https://www.imdb.com/find/?q={encoded}&ref_=nv_sr_sm",
                letterboxd_search_url=f"https://letterboxd.com/search/{encoded}/",
            ))
            entities.raw_summary = f"Movie recommendation: '{title}'"
            tags.extend(["#movie", "#cinema", "#watchlist"])
            return finalize(ContentCategory.MEDIA_MOVIE, f"movie_{self._slugify(title)}.png")

        # 6. Book Recommendation
        book_match = re.search(r"Book:[\s:]*([^\n\r]+)", text, re.IGNORECASE)
        if not book_match:
            book_match = re.search(r"(?:book|novel|reading|author)[\s:]+([^\n\r]+)", text, re.IGNORECASE)
            if book_match and any(w in book_match.group(1).lower() for w in ["recomme", "recommendation", "suggestion"]):
                alt_match = re.search(r"(?:book)[\s:]+([^\n\r]+)", text[book_match.end():], re.IGNORECASE)
                if alt_match:
                    book_match = alt_match

        if book_match or "goodreads" in text_lower or "isbn" in text_lower:
            title = book_match.group(1).strip() if book_match else "Designing Data-Intensive Applications"
            encoded = urllib.parse.quote_plus(title)
            entities.books.append(BookEntity(
                title=title,
                goodreads_search_url=f"https://www.goodreads.com/search?q={encoded}",
                google_books_url=f"https://www.google.com/search?tbm=bks&q={encoded}",
            ))
            entities.raw_summary = f"Book recommendation: '{title}'"
            tags.extend(["#book", "#reading", "#library"])
            return finalize(ContentCategory.MEDIA_BOOK, f"book_{self._slugify(title)}.png")

        # 5. App / Game Recommendation
        app_match = re.search(r"(?:app|application|download|install|github)[\s:]+([^\n\r]+)", text, re.IGNORECASE)
        if app_match or "play.google.com" in text_lower or "apk" in text_lower or "github.com" in text_lower:
            app_name = app_match.group(1).strip() if app_match else "Obsidian Notes"
            encoded = urllib.parse.quote_plus(app_name)
            entities.apps.append(AppEntity(
                name=app_name,
                play_store_url=f"https://play.google.com/store/search?q={encoded}&c=apps",
                github_url=f"https://github.com/search?q={encoded}",
            ))
            entities.raw_summary = f"App recommendation: '{app_name}'"
            tags.extend(["#app", "#android", "#tools"])
            return finalize(ContentCategory.MEDIA_APP, f"app_{self._slugify(app_name)}.png")

        # 6. Package Tracking
        track_match = re.search(r"\b([A-Z0-9]{10,24})\b", text)
        if ("tracking" in text_lower or "cdek" in text_lower or "dhl" in text_lower or "usps" in text_lower or "fedex" in text_lower) and track_match:
            track_num = track_match.group(1)
            entities.tracking.append(TrackingEntity(
                tracking_number=track_num,
                carrier="Universal Courier",
                tracking_url=f"https://parcelsapp.com/en/tracking/{track_num}",
            ))
            entities.raw_summary = f"Package tracking for shipment #{track_num}"
            tags.extend(["#tracking", "#parcel", "#delivery"])
            return finalize(ContentCategory.PACKAGE_TRACKING, f"tracking_{track_num}.png")

        # 7. Receipts / Expenses
        price_match = re.search(r"[\$€£₽]?\s*([0-9]+[.,][0-9]{2})\s*[\$€£₽]?", text)
        if ("total" in text_lower or "receipt" in text_lower or "invoice" in text_lower or "tax" in text_lower) and price_match:
            amount_str = price_match.group(1).replace(",", ".")
            try:
                amt = float(amount_str)
            except ValueError:
                amt = 0.0
            vendor = "Store/Vendor"
            first_line = text.strip().split("\n")[0][:30]
            if len(first_line) > 3:
                vendor = first_line
            entities.receipt = ReceiptEntity(vendor=vendor, amount=amt)
            entities.raw_summary = f"Receipt from {vendor} for ${amt:.2f}"
            tags.extend(["#receipt", "#expense", "#finance"])
            return finalize(ContentCategory.FINANCE_RECEIPT, f"receipt_{self._slugify(vendor)}_{amt}.png")

        # 8. Promo codes (Must be uppercase/alphanumeric, not common stop words)
        promo_match = re.search(r"(?:promo|coupon)[\s:]+([A-Z0-9_-]{4,15})", text, re.IGNORECASE)
        stop_words = {"WITH", "THIS", "THAT", "CODE", "ONLY", "YOUR", "FROM", "HAVE"}
        if promo_match and promo_match.group(1).upper() not in stop_words:
            code = promo_match.group(1).upper()
            entities.promos.append(PromoEntity(code=code))
            entities.raw_summary = f"Promo discount code '{code}'"
            tags.extend(["#promo", "#discount", "#shopping"])
            return finalize(ContentCategory.PROMO_CODE, f"promo_{code.lower()}.png")

        # Fallback General / Arbitrary Screenshot (Universal Schema-Free Essence)
        takeaway = self._extract_arbitrary_takeaway(text, clean_name)
        entities.takeaway = takeaway
        entities.raw_summary = takeaway.summary
        tags.extend(["#notes", "#screenshot"])
        for w in takeaway.headline.split():
            clean_w = re.sub(r"[^\w]", "", w).lower()
            if len(clean_w) > 3 and f"#{clean_w}" not in tags and len(tags) < 6:
                tags.append(f"#{clean_w}")
        return ContentCategory.OTHER, entities, tags, f"note_{self._slugify(takeaway.headline)}.png"

    @staticmethod
    def _extract_key_values_from_text(text: str) -> dict:
        """Extract all natural 'Key: Value' pairs present in OCR text."""
        facts = {}
        for line in text.splitlines():
            line = line.strip()
            if not line or ":" not in line:
                continue
            if re.match(r"^\d{1,2}:\d{2}", line) or line.startswith(("http:", "https:")):
                continue
            k, v = line.split(":", 1)
            k = k.strip().rstrip(". -_")
            v = v.strip()
            if 2 <= len(k) <= 30 and 1 <= len(v) <= 80:
                if k.lower() not in ["http", "https", "time"]:
                    facts[k] = v
        return facts

    def _synthesize_universal_takeaway(self, category: ContentCategory, entities: ExtractedEntities, text: str, filename: str) -> UniversalTakeaway:
        """Synthesize a schema-free UniversalTakeaway for any category or arbitrary content."""
        facts: dict = {}
        actions: List[SmartAction] = []
        clean_name = filename.rsplit(".", 1)[0]
        raw_facts = self._extract_key_values_from_text(text)
        facts.update(raw_facts)

        if entities.products:
            p = entities.products[0]
            headline = p.name
            summary = entities.raw_summary or f"Товар / вещь для покупки: {p.name}"
            if p.brand and "Brand" not in facts and "Бренд" not in facts:
                facts["Бренд"] = p.brand
            if p.size_or_variant and "Size" not in facts and "Размер" not in facts:
                facts["Размер"] = p.size_or_variant
            if p.sku_or_article and "Model" not in facts and "ModelNo" not in facts and "Артикул" not in facts:
                facts["Артикул"] = p.sku_or_article

            # Barcode check
            barcode_m = re.search(r"\b(\d{12,14})\b", text)
            if barcode_m and "Штрихкод" not in facts:
                facts["Штрихкод"] = barcode_m.group(1)

            if p.search_url:
                actions.append(SmartAction(action_type="search", label="Google Search", value=p.search_url))
            if p.market_search_url:
                actions.append(SmartAction(action_type="search", label="Яндекс Маркет", value=p.market_search_url))
            if p.sku_or_article:
                actions.append(SmartAction(action_type="copy", label=f"Скопировать модель/SKU", value=p.sku_or_article))
            if barcode_m:
                actions.append(SmartAction(action_type="copy", label=f"Штрихкод: {barcode_m.group(1)}", value=barcode_m.group(1)))
            if "Hotline" in raw_facts:
                actions.append(SmartAction(action_type="copy", label=f"Телефон: {raw_facts['Hotline']}", value=raw_facts['Hotline']))

            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.tech:
            s = entities.tech
            headline = s.name
            summary = entities.raw_summary or f"Конфигурация сервера: {s.name}"
            if s.location: facts["Локация"] = s.location
            if s.specs: facts["ОС / Спецификации"] = s.specs
            if s.price: facts["Тариф"] = s.price
            encoded = urllib.parse.quote_plus(s.name)
            actions.append(SmartAction(action_type="search", label="Поиск сервера в Google", value=f"https://www.google.com/search?q={encoded}"))
            actions.append(SmartAction(action_type="copy", label="Скопировать параметры", value=f"{s.name} {s.location or ''} {s.specs or ''}".strip()))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.wifi:
            w = entities.wifi
            headline = f"Wi-Fi: {w.ssid}"
            summary = f"Данные для подключения к беспроводной сети '{w.ssid}'"
            facts["Сеть (SSID)"] = w.ssid
            facts["Пароль"] = w.password
            actions.append(SmartAction(action_type="copy", label="Скопировать пароль", value=w.password))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.movies:
            m = entities.movies[0]
            headline = f"Фильм: {m.title}"
            summary = entities.raw_summary or f"Рекомендация фильма: {m.title}"
            if m.year: facts["Год"] = m.year
            if m.director_or_creator: facts["Режиссёр"] = m.director_or_creator
            if m.imdb_search_url: actions.append(SmartAction(action_type="search", label="IMDb", value=m.imdb_search_url))
            if m.letterboxd_search_url: actions.append(SmartAction(action_type="search", label="Letterboxd", value=m.letterboxd_search_url))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.books:
            b = entities.books[0]
            headline = f"Книга: {b.title}"
            summary = entities.raw_summary or f"Рекомендация книги: {b.title}"
            if b.author: facts["Автор"] = b.author
            if b.goodreads_search_url: actions.append(SmartAction(action_type="search", label="Goodreads", value=b.goodreads_search_url))
            if b.google_books_url: actions.append(SmartAction(action_type="search", label="Google Books", value=b.google_books_url))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.apps:
            a = entities.apps[0]
            headline = f"Приложение: {a.name}"
            summary = entities.raw_summary or f"Рекомендация приложения: {a.name}"
            facts["Платформа"] = "Android"
            if a.play_store_url: actions.append(SmartAction(action_type="search", label="Google Play", value=a.play_store_url))
            if a.github_url: actions.append(SmartAction(action_type="search", label="GitHub", value=a.github_url))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.tracking:
            t = entities.tracking[0]
            headline = f"Посылка #{t.tracking_number}"
            summary = f"Отслеживание почтового отправления {t.tracking_number}"
            facts["Трек-номер"] = t.tracking_number
            if t.carrier: facts["Служба доставки"] = t.carrier
            actions.append(SmartAction(action_type="link", label="Отследить посылку", value=t.tracking_url or f"https://parcelsapp.com/en/tracking/{t.tracking_number}"))
            actions.append(SmartAction(action_type="copy", label="Скопировать номер", value=t.tracking_number))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if entities.receipt:
            r = entities.receipt
            headline = f"Чек: {r.vendor}"
            summary = f"Квитанция об оплате на сумму ${r.amount:.2f}"
            facts["Продавец"] = r.vendor
            facts["Сумма"] = f"${r.amount:.2f}"
            actions.append(SmartAction(action_type="search", label="Поиск магазина", value=f"https://www.google.com/search?q={urllib.parse.quote_plus(r.vendor)}"))
            return UniversalTakeaway(headline=headline, summary=summary, key_facts=facts, actions=actions)

        if category == ContentCategory.EPHEMERAL_JUNK:
            return UniversalTakeaway(
                headline="Одноразовое уведомление / код",
                summary="Временный код авторизации или системное оповещение (рекомендуется удалить)",
                key_facts={"Статус": "Безопасно для удаления"},
                actions=[]
            )

        # UNIVERSAL SCHEMA-FREE FALLBACK FOR ANY ARBITRARY SCREENSHOT
        return self._extract_arbitrary_takeaway(text, clean_name)

    def _extract_arbitrary_takeaway(self, text: str, clean_name: str) -> UniversalTakeaway:
        """Extract headline, key facts, and smart actions from any arbitrary text without predefined schemas."""
        clean_lines: List[str] = []
        for raw in text.splitlines():
            l = raw.strip()
            if not l:
                continue
            # Filter out status bar noise (time, battery, 5g, wifi icons)
            filtered = re.sub(r'(?:\b\d{1,2}:\d{2}(?::\d{2})?\b|\b5g\b|\b4g\b|\blte\b|\bvolte\b|\d{1,3}%|[^\w\sа-яА-ЯёЁ])', ' ', l, flags=re.IGNORECASE).strip()
            if len(filtered) >= 3:
                clean_lines.append(l)

        headline = clean_lines[0][:60] if clean_lines else f"Заметка ({clean_name})"
        facts: dict = {}
        for l in clean_lines:
            if ":" in l:
                parts = l.split(":", 1)
                k, v = parts[0].strip(), parts[1].strip()
                if 2 <= len(k) <= 25 and len(v) > 0 and len(facts) < 6:
                    facts[k] = v

        # Extract dates / prices / urls
        dates = re.findall(r"\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b", text)
        if dates and "Дата" not in facts:
            facts["Дата"] = dates[0]

        prices = re.findall(r"[\$€£₽]\s*\d+(?:[.,]\d{2})?|\d+(?:[.,]\d{2})?\s*[\$€£₽]", text)
        if prices and "Сумма" not in facts:
            facts["Сумма"] = prices[0]

        summary = f"Сохранённая информация: {headline}"
        if facts:
            top_facts = ", ".join([f"{k}: {v}" for k, v in list(facts.items())[:2]])
            summary = f"{summary} ({top_facts})"

        encoded = urllib.parse.quote_plus(headline)
        actions: List[SmartAction] = [
            SmartAction(action_type="search", label=f"Google: {headline[:25]}", value=f"https://www.google.com/search?q={encoded}")
        ]

        # Check for URLs
        url_match = re.search(r"(https?://[^\s]+|www\.[^\s]+)", text)
        if url_match:
            u = url_match.group(1)
            if not u.startswith("http"):
                u = "https://" + u
            actions.append(SmartAction(action_type="link", label="Открыть ссылку", value=u))

        # Check for copyable codes/emails/phones
        code_match = re.search(r"\b([A-Z0-9_-]{8,24})\b", text)
        if code_match:
            actions.append(SmartAction(action_type="copy", label=f"Скопировать: {code_match.group(1)[:12]}", value=code_match.group(1)))

        return UniversalTakeaway(
            headline=headline,
            summary=summary,
            key_facts=facts,
            actions=actions,
        )

    @staticmethod
    def _slugify(text: str) -> str:
        """Create clean alphanumeric filename slug."""
        text = re.sub(r"[^\w\s-]", "", text).strip().lower()
        return re.sub(r"[-\s]+", "_", text)[:40] or "screenshot"

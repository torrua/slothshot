"""Generate realistic synthetic Android screenshots for demos and testing."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


def create_sample_screenshot(filename: Path, title: str, text_lines: list, bg_color: tuple, accent_color: tuple):
    width, height = 720, 1560  # Typical 9:19.5 phone screenshot
    img = Image.new("RGB", (width, height), color=bg_color)
    draw = ImageDraw.Draw(img)

    # Status Bar
    draw.rectangle([(0, 0), (width, 50)], fill=(20, 20, 20))
    draw.text((30, 15), "14:30  5G", fill=(200, 200, 200))
    draw.text((width - 90, 15), "87% 🔋", fill=(200, 200, 200))

    # Header Card
    draw.rounded_rectangle([(30, 80), (width - 30, 180)], radius=20, fill=accent_color)
    draw.text((50, 110), title, fill=(255, 255, 255))

    # Body Content Card
    draw.rounded_rectangle([(30, 210), (width - 30, 850)], radius=20, fill=(35, 40, 50))

    y = 250
    for line in text_lines:
        draw.text((50, y), line, fill=(230, 235, 245))
        y += 45

    # Bottom Nav Bar
    draw.rectangle([(0, height - 70), (width, height)], fill=(15, 15, 15))
    draw.ellipse([(width // 2 - 25, height - 55), (width // 2 + 25, height - 15)], fill=(100, 100, 100))

    filename.parent.mkdir(parents=True, exist_ok=True)
    img.save(filename, format="PNG")
    print(f"Generated sample: {filename.name}")


def generate_all_samples(target_dir: Path):
    target_dir.mkdir(parents=True, exist_ok=True)

    # 1. Wi-Fi Password Card
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_wifi_cafe.png",
        "Guest Wi-Fi Connection",
        [
            "Network SSID: BlueBottle_Guest",
            "Password: pour_over_coffee_2026",
            "Security: WPA2-Personal",
            "Scan QR code or enter credentials",
            "Enjoy free high-speed fiber internet",
        ],
        bg_color=(15, 23, 42),
        accent_color=(14, 116, 144),
    )

    # 2. Movie Recommendation
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_movie_dune.png",
        "Letterboxd / Cinema Recommendation",
        [
            "Movie: Dune Part Two (2024)",
            "Directed by: Denis Villeneuve",
            "Starring: Timothee Chalamet, Zendaya",
            "IMDb Rating: 8.6/10",
            "Genre: Sci-Fi, Adventure, Epic",
            "Streaming on HBO Max / Apple TV",
            "Must watch in IMAX!",
        ],
        bg_color=(17, 24, 39),
        accent_color=(180, 83, 9),
    )

    # 3. Book Recommendation
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_book_ddia.png",
        "Goodreads Book Recommendation",
        [
            "Book: Designing Data-Intensive Applications",
            "Author: Martin Kleppmann",
            "Genre: Software Architecture, Distributed Systems",
            "Publisher: O'Reilly Media",
            "Essential reading for backend engineers",
            "Key topics: Replication, Partitioning, Consensus",
        ],
        bg_color=(15, 23, 42),
        accent_color=(16, 185, 129),
    )

    # 4. App Recommendation
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_app_obsidian.png",
        "Google Play Store / Tools",
        [
            "App: Obsidian Notes",
            "Category: Productivity & Knowledge Base",
            "Developer: Dynalist Inc.",
            "Platform: Android & iOS",
            "Markdown offline-first second brain",
            "Plugins: Dataview, Canvas, Excalidraw",
        ],
        bg_color=(24, 24, 27),
        accent_color=(124, 58, 237),
    )

    # 5. Package Tracking
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_tracking_dhl.png",
        "DHL Express Shipment Notice",
        [
            "Carrier: DHL Express",
            "Tracking: CR984712035DE",
            "Status: In transit - Out for delivery",
            "Estimated Delivery: Oct 26, 2026 by 18:00",
            "Signature required upon delivery",
        ],
        bg_color=(20, 20, 20),
        accent_color=(217, 119, 6),
    )

    # 6. Restaurant Receipt
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_receipt_uber.png",
        "Uber Eats Order Confirmation",
        [
            "Vendor: Tokyo Ramen & Noodles",
            "Order #98412",
            "Total Amount: $28.50 USD",
            "Date: 2026-10-25",
            "Payment: Apple Pay (Visa ending 4128)",
            "Receipt saved for tax deductions",
        ],
        bg_color=(15, 23, 42),
        accent_color=(37, 99, 235),
    )

    # 7. Ephemeral OTP Code
    create_sample_screenshot(
        target_dir / "Screenshot_20261025_junk_otp.png",
        "Bank Security Alert",
        [
            "Your verification code is 849201",
            "Valid for 5 minutes only",
            "Do NOT share this code with anyone",
            "Single-use authentication token",
        ],
        bg_color=(30, 20, 20),
        accent_color=(225, 29, 72),
    )


if __name__ == "__main__":
    import sys
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("data/screenshots")
    generate_all_samples(out)

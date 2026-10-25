"""Command Line Interface for SlothShot."""

from __future__ import annotations

import argparse
import io
from pathlib import Path
import sys
import uvicorn
from rich.console import Console
from rich.table import Table

from slothshot.adb_client import AndroidADBClient
from slothshot.config import settings
from slothshot.engine import SlothEngine

# Fix Windows console UTF-8 output
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

console = Console(force_terminal=True, legacy_windows=False)


def scan_command(path: Path) -> None:
    """Scan and triage screenshots in directory."""
    console.print(f"[bold green][SlothShot Scanning][/bold green] {path}")
    engine = SlothEngine()
    results = engine.process_directory(path)

    if not results:
        console.print("[yellow]No screenshot images found in directory.[/yellow]")
        return

    table = Table(title="SlothShot Triage Results", border_style="green")
    table.add_column("Filename", style="cyan")
    table.add_column("Category", style="magenta")
    table.add_column("Retention Class", style="bold green")
    table.add_column("TabPFN", justify="right")
    table.add_column("Actionable Entities", style="white")
    table.add_column("Tags", style="blue")

    for r in results:
        entities_summary = []
        if r.entities.movies:
            entities_summary.append(f"Movie: {r.entities.movies[0].title}")
        if r.entities.books:
            entities_summary.append(f"Book: {r.entities.books[0].title}")
        if r.entities.apps:
            entities_summary.append(f"App: {r.entities.apps[0].name}")
        if r.entities.products:
            entities_summary.append(f"Product: {r.entities.products[0].name}")
        if r.entities.tech:
            entities_summary.append(f"Tech: {r.entities.tech.name}")
        if r.entities.wifi:
            entities_summary.append(f"WiFi: {r.entities.wifi.ssid}")
        if r.entities.tracking:
            entities_summary.append(f"Track #{r.entities.tracking[0].tracking_number}")
        if r.entities.receipt:
            entities_summary.append(f"Receipt: ${r.entities.receipt.amount}")

        table.add_row(
            r.file_name,
            r.category.value,
            r.retention_class.value,
            f"{int(r.tabpfn_score * 100)}%",
            ", ".join(entities_summary) or "-",
            " ".join(r.tags[:3]),
        )

    console.print(table)



def sync_command() -> None:
    """Pull screenshots via ADB from Android phone."""
    client = AndroidADBClient()
    devices = client.list_connected_devices()
    if not devices:
        console.print("[bold red]No Android device detected via ADB.[/bold red]")
        console.print("Ensure USB debugging is enabled or connect via 'adb connect <ip>'.")
        return

    console.print(f"[bold green]Connected Android Device:[/bold green] {devices[0]}")
    pulled = client.pull_screenshots(settings.screenshots_dir)
    console.print(f"Pulled {len(pulled)} screenshot(s) into {settings.screenshots_dir}")
    if pulled:
        scan_command(settings.screenshots_dir)


def serve_command(host: str = "0.0.0.0", port: int = 8000) -> None:
    """Start web application server."""
    console.print(f"[bold emerald]Starting SlothShot Mobile Web Butler on http://{host}:{port}[/bold emerald]")
    uvicorn.run("slothshot.web.app:app", host=host, port=port, reload=False)


def main() -> None:
    parser = argparse.ArgumentParser(description="SlothShot - Zero-Effort Android Screenshot Butler")
    subparsers = parser.add_subparsers(dest="command")

    # scan
    scan_parser = subparsers.add_parser("scan", help="Scan local screenshots folder")
    scan_parser.add_argument("path", type=str, nargs="?", default=str(settings.screenshots_dir))

    # sync
    subparsers.add_parser("sync", help="Sync screenshots from Android phone via ADB")

    # serve
    serve_parser = subparsers.add_parser("serve", help="Launch FastAPI web UI")
    serve_parser.add_argument("--host", default="0.0.0.0")
    serve_parser.add_argument("--port", type=int, default=8000)

    args = parser.parse_args()

    if args.command == "scan":
        scan_command(Path(args.path))
    elif args.command == "sync":
        sync_command()
    elif args.command == "serve":
        serve_command(host=args.host, port=args.port)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()

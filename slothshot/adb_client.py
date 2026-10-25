"""Android Debug Bridge (ADB) Client for direct Android screenshot sync."""

from __future__ import annotations

import logging
import os
from pathlib import Path
import subprocess
from typing import List, Optional

from slothshot.config import settings

logger = logging.getLogger("slothshot.adb")


class AndroidADBClient:
    """Manages wireless and USB communication with connected Android devices."""

    def __init__(self, adb_path: str = "adb", remote_dir: Optional[str] = None):
        self.adb_path = adb_path
        self.remote_dir = remote_dir or settings.android_screenshot_path

    def is_adb_available(self) -> bool:
        """Check if adb binary exists and responds."""
        try:
            res = subprocess.run([self.adb_path, "version"], capture_output=True, text=True, check=False)
            return res.returncode == 0
        except Exception:
            return False

    def list_connected_devices(self) -> List[str]:
        """List serial IDs of attached Android devices."""
        if not self.is_adb_available():
            return []
        try:
            res = subprocess.run([self.adb_path, "devices"], capture_output=True, text=True, check=False)
            devices = []
            for line in res.stdout.strip().splitlines()[1:]:
                if "\tdevice" in line:
                    devices.append(line.split("\t")[0].strip())
            return devices
        except Exception as e:
            logger.warning(f"Error querying adb devices: {e}")
            return []

    def list_remote_screenshots(self, device_id: Optional[str] = None) -> List[str]:
        """List filenames inside Android screenshot directory."""
        cmd = [self.adb_path]
        if device_id:
            cmd.extend(["-s", device_id])
        cmd.extend(["shell", "ls", "-1", self.remote_dir])

        try:
            res = subprocess.run(cmd, capture_output=True, text=True, check=False)
            if res.returncode != 0:
                # Try fallback DCIM/Screenshots
                cmd[-1] = "/sdcard/DCIM/Screenshots"
                res = subprocess.run(cmd, capture_output=True, text=True, check=False)
                if res.returncode != 0:
                    return []
                self.remote_dir = "/sdcard/DCIM/Screenshots"

            files = [
                f.strip() for f in res.stdout.strip().splitlines()
                if f.strip().lower().endswith((".png", ".jpg", ".jpeg", ".webp"))
            ]
            return files
        except Exception as e:
            logger.warning(f"Error listing remote screenshots: {e}")
            return []

    def pull_screenshots(self, destination: Path, device_id: Optional[str] = None, limit: int = 20) -> List[Path]:
        """Pull new screenshots from Android phone into local directory."""
        destination.mkdir(parents=True, exist_ok=True)
        files = self.list_remote_screenshots(device_id=device_id)
        pulled = []

        for filename in files[:limit]:
            local_target = destination / filename
            if local_target.exists():
                continue

            remote_path = f"{self.remote_dir}/{filename}"
            cmd = [self.adb_path]
            if device_id:
                cmd.extend(["-s", device_id])
            cmd.extend(["pull", remote_path, str(local_target)])

            res = subprocess.run(cmd, capture_output=True, text=True, check=False)
            if res.returncode == 0 and local_target.exists():
                pulled.append(local_target)
                logger.info(f"Pulled {filename} from Android device.")

        return pulled

    def purge_remote_file(self, filename: str, device_id: Optional[str] = None) -> bool:
        """Delete an expired screenshot directly on Android device."""
        remote_path = f"{self.remote_dir}/{filename}"
        cmd = [self.adb_path]
        if device_id:
            cmd.extend(["-s", device_id])
        cmd.extend(["shell", "rm", "-f", remote_path])
        res = subprocess.run(cmd, capture_output=True, text=True, check=False)
        return res.returncode == 0

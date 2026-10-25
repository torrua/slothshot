"""Integration tests for SlothEngine orchestrator."""

from pathlib import Path
import pytest
from slothshot.engine import SlothEngine
from slothshot.models import RetentionClass


@pytest.fixture
def engine():
    return SlothEngine()


def test_engine_process_sample_files(engine):
    sample_dir = Path("data/screenshots")
    if not sample_dir.exists():
        pytest.skip("Sample screenshots dir not generated yet")

    results = engine.process_directory(sample_dir)
    assert len(results) >= 5

    # Check that at least one movie was found
    movies = [r for r in results if r.entities.movies]
    assert len(movies) >= 1
    assert "dune" in movies[0].entities.movies[0].title.lower()

    # Check that at least one Wi-Fi was found
    wifis = [r for r in results if r.entities.wifi]
    assert len(wifis) >= 1

    # Check that all have tags
    for r in results:
        assert len(r.tags) > 0
        assert r.estimated_seconds_saved > 0

"""Tests for FastAPI endpoints."""

from fastapi.testclient import TestClient
import pytest
from slothshot.web.app import app


@pytest.fixture
def client():
    return TestClient(app)


def test_index_page(client):
    response = client.get("/")
    assert response.status_code == 200
    assert "SlothShot" in response.text


def test_manifest_json(client):
    response = client.get("/manifest.json")
    assert response.status_code == 200
    data = response.json()
    assert data["name"] == "SlothShot - Android Screenshot Butler"


def test_get_results(client):
    response = client.get("/api/results")
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_get_metrics(client):
    response = client.get("/api/metrics")
    assert response.status_code == 200
    data = response.json()
    assert "megabytes_reclaimed" in data
    assert "estimated_minutes_saved" in data
    assert "movies_found" in data


def test_audio_briefing(client):
    response = client.get("/api/audio-briefing")
    assert response.status_code in [200, 206]
    assert "audio" in response.headers.get("content-type", "")

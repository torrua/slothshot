"""Telemetry and observability module featuring Sentry Agent Tracing."""

from __future__ import annotations

import functools
import logging
import time
from typing import Any, Callable, Dict, Optional
import sentry_sdk
from sentry_sdk.integrations.logging import LoggingIntegration

from slothshot.config import settings

logger = logging.getLogger("slothshot.telemetry")


def init_telemetry() -> bool:
    """Initialize Sentry SDK with distributed Agent Tracing."""
    if not settings.sentry_dsn:
        logger.info("SENTRY_DSN not provided. Telemetry will operate in local fallback mode.")
        return False

    try:
        sentry_logging = LoggingIntegration(
            level=logging.INFO,
            event_level=logging.ERROR
        )
        sentry_sdk.init(
            dsn=settings.sentry_dsn,
            environment=settings.sentry_environment,
            traces_sample_rate=settings.sentry_traces_sample_rate,
            integrations=[sentry_logging],
            release=f"{settings.app_name}@{settings.app_version}",
        )
        sentry_sdk.set_tag("agent.name", "SlothShot")
        sentry_sdk.set_tag("agent.type", "multimodal-screenshot-triage")
        sentry_sdk.set_tag("hardware.platform", "Android-Companion")
        logger.info("Sentry Agent Tracing successfully initialized.")
        return True
    except Exception as e:
        logger.warning(f"Failed to initialize Sentry: {e}")
        return False


def trace_agent_span(op: str, description: str):
    """Decorator to record custom agent spans with input/output telemetry."""
    def decorator(func: Callable):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            start_time = time.perf_counter()
            with sentry_sdk.start_span(op=op, name=description) as span:
                span.set_data("agent.op", op)
                span.set_data("agent.desc", description)
                try:
                    result = func(*args, **kwargs)
                    span.set_status("ok")
                    return result
                except Exception as exc:
                    span.set_status("internal_error")
                    span.set_data("error.message", str(exc))
                    sentry_sdk.capture_exception(exc)
                    raise
                finally:
                    duration_ms = (time.perf_counter() - start_time) * 1000
                    span.set_data("duration_ms", duration_ms)
        return wrapper
    return decorator


def record_triage_decision(
    screenshot_id: str,
    category: str,
    retention_class: str,
    tabpfn_score: float,
    extracted_entities_count: int,
    bytes_reclaimed: int
) -> None:
    """Record metrics and tags for a screenshot triage decision in Sentry."""
    sentry_sdk.set_tag("triage.category", category)
    sentry_sdk.set_tag("triage.retention_class", retention_class)
    sentry_sdk.set_context(
        "sloth_metrics",
        {
            "screenshot_id": screenshot_id,
            "category": category,
            "retention_class": retention_class,
            "tabpfn_confidence": tabpfn_score,
            "entities_found": extracted_entities_count,
            "bytes_reclaimed": bytes_reclaimed,
        }
    )

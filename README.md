# 🦥 SlothShot

> **The Zero-Effort Android Screenshot Triage & Second Brain Butler**  
> *Powered by Google Gemma 2, Prior Labs TabPFN, Sentry Agent Tracing, ElevenLabs & Render.*

[![Python 3.10+](https://img.shields.io/badge/python-3.10+-blue.svg)](https://www.python.org/downloads/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Hacktoberfest 2026](https://img.shields.io/badge/Hacktoberfest-2026%20Week%203-emerald.svg)](https://dev.to/challenges/hacktoberfest-week3-2026-10-19)

---

## 📖 The Sloth Manifesto: Why SlothShot Exists

On October 20, the world celebrates **International Sloth Day**. Sloths conserve their energy, avoid unnecessary movement, and let the world come to them.

Modern smartphone users are digital hoarders out of sheer laziness:
- You see a Wi-Fi password in a café, but you're too lazy to type it -> **Screenshot**.
- A friend mentions a movie on Netflix, you don't want to switch apps -> **Screenshot**.
- A book recommendation in an article -> **Screenshot**.
- An OTP security code or a food courier delivery map -> **Screenshot**.

Within a year, your Android phone gallery is an unsearchable digital wasteland of **3,000+ files** named `Screenshot_20261025-142211.png` eating up **4 to 8 Gigabytes** of storage. Finding that one receipt or Wi-Fi password takes 20 minutes of endless scrolling.

**SlothShot does the work so you don't have to.**

---

## ⚡ Key Features

- 🧠 **Dual-AI Architecture**:
  - **Prior Labs TabPFN Foundation Model**: Classifies the lifecycle retention policy (`EXTRACT_AND_PURGE`, `ARCHIVE_WITH_TAGS`, `EPHEMERAL_JUNK`) directly from tabular structural features without retraining.
  - **Google DeepMind Gemma 2**: Reads the screenshot context, extracts structured actionable entities, and generates descriptive semantic filenames.
- 🎬 **Actionable Artifacts Extraction**:
  - **Movies & TV Shows**: Extracts title, director, and year with 1-click links to **IMDb** and **Letterboxd**.
  - **Books**: Extracts title and author with 1-click links to **Goodreads** and **Google Books**.
  - **Apps & Repos**: Extracts app names with direct search links to **Google Play Store** and **GitHub**.
  - **Wi-Fi Credentials**: Extracts SSID & Password with a 1-click **Copy Password** button.
  - **Package Tracking**: Extracts carrier & tracking codes with instant package lookup.
  - **Receipts & Invoices**: Extracts merchant, date, and amount for tax/expense filing.
- 🏷️ **Smart Auto-Tagging**: Generates `#movie`, `#book`, `#app`, `#wifi`, `#tracking`, `#receipt`, `#junk` for instant filtering.
- 🗑️ **The Lazy Payoff (1-Click Purge)**: Delete single-use OTPs, battery screenshots, and safe-to-delete media in one tap, saving hundreds of megabytes.
- 📱 **Android ADB Sync**: Connect your phone via USB or Wi-Fi; SlothShot pulls, analyzes, and cleans screenshots automatically.
- 🎙️ **ElevenLabs Audio Briefing**: Generates a sleepy, relaxing voice report from the Sloth butler while you rest.
- 🛡️ **Sentry Agent Tracing**: Full telemetry on OCR latency, TabPFN classification scores, and agent execution spans.

---

## 🏛️ Architecture

```mermaid
flowchart TD
    Android["📱 Android Phone<br/>(ADB Sync or Gallery Upload)"] --> Ingest["📥 SlothShot Ingestion Engine"]

    subgraph Dual_AI["🧠 Dual-Brain Open AI Pipeline"]
        Ingest --> OCR["RapidOCR Onnx Engine<br/>(Multilingual text extraction)"]
        OCR --> Features["Tabular Feature Extractor<br/>(size, aspect ratio, text density, burst)"]
        Features --> TabPFN["TabPFN (Prior Labs Foundation Model)<br/>Predicts Retention Policy<br/>(Extract & Purge vs Archive vs Junk)"]
        
        OCR --> Gemma["Gemma 2 (Google DeepMind)<br/>Semantic Entity Extraction & Auto-Tagging<br/>(Movies, Books, Apps, Wi-Fi, Tracking)"]
        
        TabPFN & Gemma --> Consensus["Reconciliation & Decision Engine"]
    end

    Consensus --> PWA["📱 Mobile-First PWA Web Interface"]
    Consensus --> Purge["🗑️ Storage Reclaimed (1-Click)"]
    Consensus --> Voice["🎙️ ElevenLabs Sleepy Voice Summary"]

    Dual_AI -.-> Sentry["🛡️ Sentry Agent Tracing"]
```

---

## 🚀 Quickstart

### 1. Run with Docker (Recommended)

```bash
git clone https://github.com/torrua/slothshot.git
cd slothshot
docker compose up -d
```
Open **http://localhost:8000** in your browser.

### 2. Local Python Installation

```bash
git clone https://github.com/torrua/slothshot.git
cd slothshot
pip install -r requirements.txt
pip install -e .
```

### 3. Generate Sample Screenshots for Demo

```bash
python examples/generate_samples.py data/screenshots
```

### 3. Run CLI Triage

```bash
# Scan local folder
slothshot scan data/screenshots

# Sync directly from connected Android phone
slothshot sync
```

### 4. Launch Mobile Web App

```bash
slothshot serve --port 8000
```
Open **http://localhost:8000** on your browser or smartphone.

---

## 🏆 Hacktoberfest 2026 Partner Tracks

| Category | Partner | How SlothShot Uses It |
| :--- | :--- | :--- |
| **Featured ($200)** | **Gemma** | Multimodal semantic entity extraction, movie/book identification, auto-tagging. |
| **Featured ($200)** | **TabPFN** | Prior Labs tabular foundation model predicting screenshot lifecycle retention from structural features. |
| **Featured ($200)** | **Render** | 1-click cloud deployment via `render.yaml`. |
| **Partner ($100)** | **Sentry** | Distributed Agent Tracing instrumenting OCR, TabPFN, and Gemma execution spans. |
| **Partner ($100)** | **ElevenLabs** | Sleepy voice debriefing from the Sloth butler summarizing reclaimed storage. |

---

## 📄 License

MIT License. Built with ❤️ for lazy humans everywhere.

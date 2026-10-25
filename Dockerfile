FROM python:3.11-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    DEBIAN_FRONTEND=noninteractive

# System dependencies for RapidOCR, Pillow & PyTorch
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Pre-install lightweight CPU-only PyTorch (avoids 4GB of NVIDIA CUDA packages)
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .
RUN pip install -e .
RUN python examples/generate_samples.py data/screenshots

EXPOSE 8000

CMD ["uvicorn", "slothshot.web.app:app", "--host", "0.0.0.0", "--port", "8000"]

"""
CLIP Embedding Microservice
Generates 512-dimensional image embeddings using OpenAI's CLIP model.
Used by the Spring Boot search-by-image backend for visual similarity search.
"""

import io
import logging
from contextlib import asynccontextmanager

import torch
import clip
from PIL import Image
from fastapi import FastAPI, File, UploadFile, HTTPException
from pydantic import BaseModel
import httpx

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Global model references
model = None
preprocess = None
device = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load CLIP model on startup."""
    global model, preprocess, device
    device = "cuda" if torch.cuda.is_available() else "cpu"
    logger.info(f"Loading CLIP model on {device}...")
    model, preprocess = clip.load("ViT-B/32", device=device)
    model.eval()
    logger.info("CLIP model loaded successfully")
    yield


app = FastAPI(
    title="CLIP Embedding Service",
    description="Generate image embeddings using CLIP ViT-B/32",
    version="1.0.0",
    lifespan=lifespan,
)


class ImageUrlRequest(BaseModel):
    image_url: str


class EmbeddingResponse(BaseModel):
    embedding: list[float]
    model: str = "clip-vit-base-patch32"


def generate_embedding(image: Image.Image) -> list[float]:
    """Generate a normalized 512-dim embedding from a PIL Image."""
    image_input = preprocess(image).unsqueeze(0).to(device)
    with torch.no_grad():
        features = model.encode_image(image_input)
        features = features / features.norm(dim=-1, keepdim=True)
    return features.squeeze().cpu().tolist()


@app.post("/embed/image", response_model=EmbeddingResponse)
async def embed_image_file(file: UploadFile = File(...)):
    """Generate embedding from an uploaded image file."""
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        embedding = generate_embedding(image)
        return EmbeddingResponse(embedding=embedding)
    except Exception as e:
        logger.error(f"Failed to process uploaded image: {e}")
        raise HTTPException(status_code=400, detail=f"Failed to process image: {str(e)}")


@app.post("/embed/url", response_model=EmbeddingResponse)
async def embed_image_url(request: ImageUrlRequest):
    """Generate embedding from an image URL."""
    try:
        headers = {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            ),
            "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Referer": request.image_url,
        }
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            response = await client.get(request.image_url, headers=headers)
            response.raise_for_status()

        image = Image.open(io.BytesIO(response.content)).convert("RGB")
        embedding = generate_embedding(image)
        return EmbeddingResponse(embedding=embedding)
    except httpx.HTTPError as e:
        logger.error(f"Failed to download image from URL: {e}")
        raise HTTPException(status_code=400, detail=f"Failed to download image: {str(e)}")
    except Exception as e:
        logger.error(f"Failed to process image from URL: {e}")
        raise HTTPException(status_code=400, detail=f"Failed to process image: {str(e)}")


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok", "model_loaded": model is not None}

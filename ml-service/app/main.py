import hmac
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from starlette.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from app.inference import load_artifact, predict
from app.schemas import PredictionRequest, PredictionResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    key = os.environ.get("ML_API_KEY", "")
    if len(key) < 32:
        raise RuntimeError("ML_API_KEY must contain at least 32 characters")
    app.state.key = key
    app.state.bundle = load_artifact(Path(os.environ.get("MODEL_PATH", "artifacts/model.joblib")))
    yield


app = FastAPI(title="CashFlow private inference", version="1.0.0", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


class BodyLimit:
    """Bound streamed bodies through the public ASGI protocol before parsing."""
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["method"] not in ("POST", "PUT", "PATCH"):
            return await self.app(scope, receive, send)
        chunks, size = [], 0
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            chunk = message.get("body", b"")
            size += len(chunk)
            if size > 131072:
                return await JSONResponse({"detail": "Request exceeds 128 KB"}, status_code=413)(scope, receive, send)
            chunks.append(chunk)
            if not message.get("more_body", False):
                break
        delivered = False

        async def bounded_receive():
            nonlocal delivered
            if delivered:
                return await receive()
            delivered = True
            return {"type": "http.request", "body": b"".join(chunks), "more_body": False}

        return await self.app(scope, bounded_receive, send)


app.add_middleware(BodyLimit)


@app.get("/health")
def health():
    bundle = app.state.bundle
    return {"status": "ok", "modelLoaded": bundle is not None, "modelVersion": bundle["version"] if bundle else "baseline-v1"}


@app.post("/predict", response_model=PredictionResponse)
async def prediction(request: PredictionRequest, x_internal_key: str = Header(default="")):
    if not hmac.compare_digest(x_internal_key, app.state.key):
        raise HTTPException(status_code=401, detail="Unauthorized")
    return await run_in_threadpool(predict, request, app.state.bundle)

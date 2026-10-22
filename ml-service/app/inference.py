from datetime import timedelta
from pathlib import Path
import warnings

import joblib
import numpy as np
from sklearn.exceptions import InconsistentVersionWarning
from threadpoolctl import threadpool_limits

from app.features import FEATURE_VERSION, features
from app.schemas import HistoryPoint, PredictionRequest, PredictionResponse


def load_artifact(path: Path):
    """Load only a trusted, operator-configured local artifact. Never accept uploads."""
    if not path.is_file():
        return None
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", InconsistentVersionWarning)
            bundle = joblib.load(path)
        if not isinstance(bundle, dict) or bundle.get("featureVersion") != FEATURE_VERSION:
            return None
        if bundle.get("dataSource") != "synthetic" or not bundle.get("version"):
            return None
        if not hasattr(bundle.get("model"), "predict") or bundle["model"].n_features_in_ != 12:
            return None
        return bundle
    except Exception:
        return None


def baseline(request: PredictionRequest, reason: str) -> PredictionResponse:
    history = request.history[-56:]
    insufficient = len(history) < 28
    result = []
    for i in range(request.horizonDays):
        day = request.startDate + timedelta(days=i)
        same = [p.netFlow for p in history if p.date.weekday() == day.weekday()]
        result.append(HistoryPoint(date=day, netFlow=round(float(np.mean(same)), 2) if same and not insufficient else 0))
    return PredictionResponse(
        predictions=result,
        modelName="Scheduled-only baseline" if insufficient else "Historical weekday baseline",
        modelVersion="python-baseline-v1",
        limitations=[reason, "Missing historical days must be supplied as zero; completeness of records is assumed. No calibrated uncertainty interval."],
    )


def predict(request: PredictionRequest, bundle=None) -> PredictionResponse:
    if len(request.history) < 28:
        return baseline(request, "Fewer than 28 historical days: unplanned movement is assumed zero.")
    if not request.allowSyntheticModel:
        return baseline(request, "Uses this business's weekday history. The synthetic demonstration model is not validated for real businesses.")
    if bundle is None:
        return baseline(request, "No valid trained artifact is available. A transparent historical baseline was used.")
    if bundle.get("selectedOnValidation") != "candidate":
        return baseline(request, "The weekday baseline performed better on chronological validation and was selected.")
    past = [p.netFlow for p in request.history]
    points = []
    # Limit OpenMP to avoid small requests monopolizing all workstation cores.
    with threadpool_limits(limits=1):
        for i in range(request.horizonDays):
            day = request.startDate + timedelta(days=i)
            estimate = float(bundle["model"].predict(features(day, past).reshape(1, -1))[0])
            if not np.isfinite(estimate) or abs(estimate) > 1e12:
                return baseline(request, "Model output failed validation. Historical baseline used.")
            estimate = round(estimate, 2)
            past.append(estimate)  # recursive estimates only; no future actuals
            points.append(HistoryPoint(date=day, netFlow=estimate))
    return PredictionResponse(
        predictions=points, modelName="Synthetic demonstration · HistGradientBoosting",
        modelVersion=bundle["version"],
        limitations=["Trained and evaluated on synthetic businesses only; not validated on real bank cash flows.",
                     "Forecast errors can accumulate over 30 recursive days. No calibrated uncertainty interval."],
    )

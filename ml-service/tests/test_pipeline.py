from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.features import features, training_rows
from app.inference import load_artifact, predict
from app.main import app
from app.schemas import HistoryPoint, PredictionRequest
from training.generate import generate
from training.train import evaluate


def request(days=56, **overrides):
    start = date(2026, 1, 1)
    payload = dict(startDate=start, history=[HistoryPoint(date=start - timedelta(days=days-i), netFlow=100) for i in range(days)])
    return PredictionRequest(**(payload | overrides))


def test_features_exclude_current_and_future_targets():
    dates = [date(2025, 1, 1) + timedelta(days=i) for i in range(60)]
    values = list(range(60))
    x, y = training_rows(dates, values)
    altered = values.copy()
    altered[40:] = [99999] * 20
    changed, _ = training_rows(dates, altered)
    np.testing.assert_array_equal(x[:13], changed[:13])  # target day 40 not in its own features
    assert y[0] == 28
    assert x[0, 5] == 27


@pytest.mark.parametrize("days", [0, 7, 27])
def test_insufficient_history_is_zero_baseline(days):
    response = predict(request(days))
    assert response.modelName == "Scheduled-only baseline"
    assert len(response.predictions) == 30
    assert all(p.netFlow == 0 for p in response.predictions)


def test_baseline_and_synthetic_opt_in():
    response = predict(request())
    assert all(p.netFlow == 100 for p in response.predictions)
    assert "not validated" in response.limitations[0]
    assert predict(request(allowSyntheticModel=True)).modelVersion == "python-baseline-v1"


@pytest.mark.parametrize("horizon", [0, 31, 10000])
def test_horizon_bound(horizon):
    with pytest.raises(ValidationError):
        request(horizonDays=horizon)


def test_duplicate_unsorted_future_and_nonfinite_history_rejected():
    valid = request().model_dump()
    for values in [valid["history"][::-1], valid["history"] + [valid["history"][-1]]]:
        with pytest.raises(ValidationError):
            PredictionRequest(**(valid | {"history": values}))
    with pytest.raises(ValidationError):
        request(startDate=date(2025, 12, 30))
    with pytest.raises(ValidationError):
        HistoryPoint(date=date.today(), netFlow=float("nan"))
    with pytest.raises(ValueError):
        features(date.today(), [1] * 27)


def test_missing_corrupt_artifacts_fail_safely(tmp_path):
    assert load_artifact(tmp_path / "missing") is None
    bad = tmp_path / "bad.joblib"
    bad.write_bytes(b"not a model")
    assert load_artifact(bad) is None


def test_synthetic_generation_is_reproducible_and_balance_matches():
    a, b = generate(days=120), generate(days=120)
    assert a.equals(b)
    net = a.unplanned_net + a.scheduled_inflow - a.recurring_outflow
    np.testing.assert_allclose(a.closing_balance, 180000 + net.cumsum(), atol=0.01)
    assert a.synthetic.all()


def test_recursive_evaluation_does_not_use_future_actuals():
    class RecordingModel:
        def __init__(self): self.rows = []
        def predict(self, row): self.rows.append(row.copy()); return [25.]
    dates = [date(2025, 1, 1) + timedelta(days=i) for i in range(90)]
    model = RecordingModel()
    evaluate(model, dates, list(range(90)), 60, 90)
    assert model.rows[1][0, 5] == 25  # previous predicted value, not actual day 60


def test_http_auth_health_and_size(monkeypatch, tmp_path):
    monkeypatch.setenv("ML_API_KEY", "test-only-key-at-least-32-characters")
    monkeypatch.setenv("MODEL_PATH", str(tmp_path / "missing"))
    with TestClient(app) as client:
        assert client.get("/health").json()["modelLoaded"] is False
        payload = request().model_dump(mode="json")
        assert client.post("/predict", json=payload).status_code == 401
        response = client.post("/predict", json=payload, headers={"X-Internal-Key": "test-only-key-at-least-32-characters"})
        assert response.status_code == 200
        assert len(response.json()["predictions"]) == 30
        assert client.post("/predict", content=b" " * 131073).status_code == 413


def test_trained_artifact_inference_if_present():
    path = Path("artifacts/model.joblib")
    if not path.exists():
        pytest.skip("Run python -m training.train for artifact inference test")
    bundle = load_artifact(path)
    assert bundle is not None
    result = predict(request(allowSyntheticModel=True), bundle)
    assert result.modelVersion == bundle["version"]
    assert len(result.predictions) == 30

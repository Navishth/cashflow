"""Chronological, recursive 30-day evaluation; test is untouched during selection."""
import hashlib
import json
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import HistGradientBoostingRegressor
from threadpoolctl import threadpool_limits

from app.features import FEATURE_VERSION, features, training_rows
from training.generate import generate


def metrics(actual, predicted):
    error = np.asarray(predicted) - np.asarray(actual)
    return {"mae": round(float(np.abs(error).mean()), 2), "rmse": round(float(np.sqrt((error ** 2).mean())), 2), "bias": round(float(error.mean()), 2)}


def evaluate(model, dates, values, start, end):
    actual, candidate, baseline = [], [], []
    for origin in range(start, end - 29, 30):
        past = list(values[:origin])
        fixed = list(zip(dates[max(0, origin - 56):origin], values[max(0, origin - 56):origin]))
        for i in range(origin, origin + 30):
            estimate = float(model.predict(features(dates[i], past).reshape(1, -1))[0])
            candidate.append(estimate)
            past.append(estimate)
            same = [v for d, v in fixed if d.weekday() == dates[i].weekday()]
            baseline.append(float(np.mean(same)))
            actual.append(values[i])
    return {"candidate": metrics(actual, candidate), "weekdayBaseline": metrics(actual, baseline), "evaluatedDays": len(actual)}


def train():
    frame = generate()
    dates, values = frame.date.tolist(), frame.unplanned_net.tolist()
    train_end, val_end = 730, 910
    x, y = training_rows(dates[:train_end], values[:train_end])
    with threadpool_limits(limits=1):
        candidate = HistGradientBoostingRegressor(max_iter=150, max_leaf_nodes=15, l2_regularization=10, learning_rate=0.05, random_state=42)
        candidate.fit(x, y)
        validation = evaluate(candidate, dates, values, train_end, val_end)
        test = evaluate(candidate, dates, values, val_end, len(values))
    selected = "candidate" if validation["candidate"]["mae"] < validation["weekdayBaseline"]["mae"] else "weekdayBaseline"
    version = "synthetic-hgbr-v1-" + hashlib.sha256(frame.to_csv(index=False).encode()).hexdigest()[:12]
    bundle = {"model": candidate, "version": version, "featureVersion": FEATURE_VERSION, "dataSource": "synthetic", "selectedOnValidation": selected}
    Path("artifacts").mkdir(exist_ok=True)
    joblib.dump(bundle, "artifacts/model.joblib")
    report = {"dataSource": "synthetic; seed 42; not bank data", "modelVersion": version,
              "target": "Daily unplanned inflows minus unplanned outflows; excludes scheduled receipts and recurring commitments",
              "training": {"start": str(dates[28]), "end": str(dates[train_end - 1]), "rows": len(y)},
              "validation": {"start": str(dates[train_end]), "end": str(dates[val_end - 1]), **validation},
              "test": {"start": str(dates[val_end]), "end": str(dates[-1]), **test},
              "selection": selected, "protocol": "Non-overlapping recursive 30-day origins; no future actuals used inside each horizon. No refit on validation/test.",
              "limitation": "Synthetic demonstration only. Real accounts use a historical baseline. No calibrated uncertainty intervals."}
    Path("artifacts/evaluation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    train()

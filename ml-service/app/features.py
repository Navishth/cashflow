"""One shared feature implementation for training and recursive inference."""
from datetime import date
import math
import numpy as np

FEATURE_VERSION = "lags-v1"
MIN_HISTORY = 28


def features(day: date, past: list[float]) -> np.ndarray:
    if len(past) < MIN_HISTORY:
        raise ValueError("At least 28 past days are required")
    recent = np.asarray(past[-28:], dtype=float)
    if not np.isfinite(recent).all():
        raise ValueError("History must be finite")
    # past excludes the prediction day; never slice from a future target array here.
    return np.array([
        day.weekday(), day.day, day.month,
        math.sin(2 * math.pi * day.timetuple().tm_yday / 365.25),
        math.cos(2 * math.pi * day.timetuple().tm_yday / 365.25),
        past[-1], past[-7], past[-14], past[-28],
        recent[-7:].mean(), recent.mean(), recent.std(),
    ], dtype=float)


def training_rows(dates: list[date], values: list[float]):
    if len(dates) != len(values) or len(values) <= MIN_HISTORY:
        raise ValueError("Insufficient or misaligned training series")
    x = np.stack([features(dates[i], values[:i]) for i in range(MIN_HISTORY, len(values))])
    return x, np.asarray(values[MIN_HISTORY:], dtype=float)

from datetime import date, timedelta
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class HistoryPoint(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    date: date
    netFlow: Annotated[float, Field(ge=-1e12, le=1e12)]


class PredictionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schemaVersion: Literal["1"] = "1"
    history: Annotated[list[HistoryPoint], Field(min_length=0, max_length=730)]
    startDate: date
    horizonDays: Annotated[int, Field(ge=1, le=30)] = 30
    allowSyntheticModel: bool = False

    @model_validator(mode="after")
    def chronological(self):
        if not date(2000, 1, 1) <= self.startDate <= date(2100, 1, 1):
            raise ValueError("startDate is outside the supported range")
        dates = [point.date for point in self.history]
        if any(b - a != timedelta(days=1) for a, b in zip(dates, dates[1:])):
            raise ValueError("History must be ordered, unique, and daily without gaps")
        if dates and dates[-1] != self.startDate - timedelta(days=1):
            raise ValueError("History must end one day before the forecast starts")
        return self


class PredictionResponse(BaseModel):
    predictions: list[HistoryPoint]
    modelName: str
    modelVersion: str
    limitations: list[str]

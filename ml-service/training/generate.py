"""Reproducible synthetic transactions, schedules, and daily cash balances."""
import argparse
from pathlib import Path
import numpy as np
import pandas as pd


def generate(seed=42, days=1095):
    rng = np.random.default_rng(seed)
    dates = pd.date_range("2022-01-01", periods=days)
    rows = []
    balance = 180000.0
    delayed = {}
    for i, stamp in enumerate(dates):
        season = 1 + 0.18 * np.sin(2 * np.pi * i / 365.25)
        sales = max(0, (1600 + 300 * stamp.dayofweek) * season + rng.normal(0, 220))
        costs = max(0, 1100 + rng.normal(0, 120))
        irregular = float(rng.uniform(2000, 7000)) if rng.random() < 0.025 else 0
        scheduled_in = delayed.pop(i, 0.0)
        delay_days = 0
        if stamp.day in (10, 25):
            delay_days = int(rng.choice([0, 3, 7], p=[0.6, 0.3, 0.1]))
            delayed[i + delay_days] = delayed.get(i + delay_days, 0) + 18000
            if delay_days == 0:
                scheduled_in += delayed.pop(i)
        recurring_out = (18000 if stamp.day == 1 else 0) + (1800 if stamp.dayofweek == 0 else 0)
        net = round(sales - costs - irregular, 2)
        balance = round(balance + net + scheduled_in - recurring_out, 2)
        rows.append(dict(date=stamp.date(), unplanned_inflow=round(sales, 2),
                         unplanned_outflow=round(costs + irregular, 2), unplanned_net=net,
                         scheduled_inflow=scheduled_in, recurring_outflow=recurring_out,
                         receipt_delay_days=delay_days, closing_balance=balance, synthetic=True))
    return pd.DataFrame(rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="../data/generated/synthetic.csv")
    args = parser.parse_args()
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    generate().to_csv(path, index=False)
    print(f"Wrote synthetic daily data to {path}")

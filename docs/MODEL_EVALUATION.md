# Model methodology and measured results

## Data

The runnable model uses reproducible **synthetic** data, seed 42, spanning 2022-01-01 through 2024-12-30. The generator includes weekday/seasonal sales, routine and irregular expenses, scheduled receipts with 0/3/7-day delays, monthly rent, weekly commitments, and cash-balance evolution from a known opening balance.

UCI Online Retail II was investigated as requested. Its [official dataset page](https://archive.ics.uci.edu/dataset/502/online%2Bretail) provides a 43.5 MB workbook and a CC BY 4.0 license. Attribution: Daqing Chen (2012), *Online Retail II*, DOI [10.24432/C5CG6D](https://doi.org/10.24432/C5CG6D).

It contains retail invoice activity, not bank settlement dates, expenses, or actual cash balances. It cannot establish the application's unplanned-cash target. Therefore, it is not used to train the shipped demonstration model. No real customer banking data or raw UCI customer data is bundled.

## Target and features

Target: daily **unplanned inflows minus unplanned outflows**. Scheduled receipts, delayed scheduled receipts, and recurring payments are excluded and handled separately by Java.

Shared training/inference features: weekday, day of month, month, annual sine/cosine, lag 1/7/14/28, trailing 7/28-day means, and trailing 28-day standard deviation. Feature construction accepts only past observations. During each multi-step forecast, prior predictions are appended recursively; future actual values are never used.

## Evaluation protocol

- Training: 2022-01-29–2023-12-31, 702 feature rows.
- Validation partition: 2024-01-01–2024-06-28, 180 evaluated days.
- Test partition: 2024-06-29–2024-12-30, 180 evaluated days in complete 30-day blocks; the final five days are excluded.
- Non-overlapping rolling origins, 30 recursive days at each origin.
- Baseline: same weekday mean from the preceding 56 days, fixed at each origin.
- Candidate: HistGradientBoostingRegressor, 150 iterations, 15 max leaf nodes, learning rate 0.05, L2=10, random_state=42.
- Select on validation MAE only; no training/refit on validation or test.

Actual run with scikit-learn 1.7.2, NumPy 2.3.3, Python 3.11.9:

| Split/model | MAE (₹/day) | RMSE (₹/day) | Bias (₹/day) |
| --- | ---: | ---: | ---: |
| Validation: candidate | 304.84 | 490.15 | -25.02 |
| Validation: weekday baseline | 324.85 | 516.69 | -63.68 |
| Test: candidate | 338.24 | 674.43 | -29.15 |
| Test: weekday baseline | 341.91 | 667.78 | 85.18 |

Candidate selected on validation. Test MAE improved only slightly; test RMSE was worse. These are synthetic daily net-flow errors, **not** accuracy percentages, forecast confidence, or evidence of performance on real businesses.

Model version: `synthetic-hgbr-v1-1374d58a72f2`. Machine-readable evidence: `ml-service/artifacts/evaluation.json`. The artifact is generated locally and excluded from Git; Docker trains it during the build.

## Runtime choices and limitations

Only demo businesses opt into the synthetic artifact. Real businesses use their own historical weekday baseline. Fewer than 28 days yields a scheduled-only forecast. Missing historical days count as zero, making completeness a material assumption.

Synthetic patterns do not represent all sectors, scales, or shocks. The generator is one controlled business process; no claim of cross-business generalization is made. Recursive errors accumulate. No calibrated uncertainty intervals or confidence percentages are displayed.

Missing, corrupt, incompatible, or invalid artifacts fall back safely. Artifacts must come from the trusted training pipeline: joblib/pickle files can execute code and must never be accepted as user uploads.

## Reproduce

```powershell
Set-Location ml-service
..\.venv\Scripts\python.exe -m training.generate
..\.venv\Scripts\python.exe -m training.train
..\.venv\Scripts\python.exe -m pytest -q
```

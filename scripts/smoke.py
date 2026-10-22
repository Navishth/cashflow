"""Live-service integration smoke test. Only creates synthetic test records."""
import argparse
import json
from pathlib import Path
import re
import secrets
import uuid

import httpx

ROOT = Path(__file__).resolve().parent.parent
CONFIG = json.loads((ROOT / ".local/environment.json").read_text(encoding="utf-8-sig"))
API = "http://127.0.0.1:8080/api/v1"


def csrf(client):
    response = client.get(API + "/auth/csrf")
    response.raise_for_status()
    data = response.json()
    client.headers[data["headerName"]] = data["token"]


def call(client, method, path, payload=None, expected=200):
    response = client.request(method, API + path, json=payload)
    assert response.status_code == expected, f"{method} {path}: expected {expected}, received {response.status_code}"
    return response.json() if response.content else None


def verify(client, email):
    for file in Path(CONFIG["MAIL_DIRECTORY"]).glob("*.eml"):
        text = file.read_text(encoding="utf-8")
        if f"To: {email}\n" in text and "Verify your" in text:
            match = re.search(r"#token=([A-Za-z0-9_-]+)", text)
            assert match, "Verification message contains no link"
            call(client, "POST", "/auth/verify-email", {"token": match.group(1)})
            return
    raise AssertionError("No local verification message for the synthetic account")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify-email")
    args = parser.parse_args()
    with httpx.Client(timeout=15) as client:
        csrf(client)
        if args.verify_email:
            verify(client, args.verify_email)
            print("Synthetic browser-test email verified through the live API.")
            return
        email = f"smoke-{uuid.uuid4().hex}@example.test"
        password = secrets.token_urlsafe(24)
        call(client, "POST", "/auth/register", {"email": email, "password": password, "displayName": "Synthetic Smoke Test"})
        verify(client, email)
        old_cookie = client.cookies.get("CFTSESSION")
        call(client, "POST", "/auth/login", {"email": email, "password": password})
        assert client.cookies.get("CFTSESSION") != old_cookie, "Login must rotate session identifier"
        csrf(client)
        assert call(client, "GET", "/auth/me")["email"] == email
        business = call(client, "POST", "/businesses", {
            "name": "Synthetic integration demo", "currency": "INR", "timezone": "Asia/Kolkata",
            "openingDate": "2026-01-01", "openingBalance": 180000, "reserve": 120000, "demo": True,
        }, 201)
        forecast = call(client, "POST", "/forecasts/generate", {})
        assert len(forecast["points"]) == 30
        assert forecast["modelVersion"].startswith("synthetic-hgbr-v1"), "Expected live trained Python inference"
        receipt = next(e for e in forecast["events"] if e["type"] == "INFLOW")
        scenario = call(client, "POST", "/scenarios", {
            "name": "Synthetic delayed receipt", "baselineId": forecast["id"],
            "changes": [{"kind": "RESCHEDULE", "targetEventId": receipt["id"], "newDate": forecast["endDate"]}],
        }, 201)
        comparison = call(client, "POST", f"/scenarios/{scenario['id']}/simulate", {})
        repeat = call(client, "GET", f"/scenarios/{scenario['id']}/compare")
        assert repeat["points"] == comparison["points"]
        assert call(client, "GET", f"/cash-flows/{receipt['id']}")["expectedDate"] == receipt["date"]
        assert call(client, "GET", f"/forecasts/{forecast['id']}")["points"] == forecast["points"]
        assert comparison["finalBalanceChange"] == 0
        cors = client.options(API + "/cash-flows", headers={"Origin": "http://localhost:5173", "Access-Control-Request-Method": "POST"})
        assert cors.headers.get("access-control-allow-origin") == "http://localhost:5173"
        bad = client.options(API + "/cash-flows", headers={"Origin": "https://untrusted.example", "Access-Control-Request-Method": "POST"})
        assert bad.status_code == 403
        report = {"registration": "PASS", "emailVerification": "PASS", "sessionRotation": "PASS",
                  "businessSetup": "PASS", "forecastDays": len(forecast["points"]),
                  "liveModelVersion": forecast["modelVersion"], "scenarioPersistence": "PASS",
                  "financialRecordImmutability": "PASS", "corsAllowlist": "PASS",
                  "businessId": business["id"], "forecastId": forecast["id"]}
        call(client, "POST", "/auth/logout", expected=204)
        call(client, "GET", "/auth/me", expected=401)
        report["logoutInvalidation"] = "PASS"
        (ROOT / ".local/smoke-result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

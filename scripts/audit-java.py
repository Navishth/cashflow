"""Query OSV for the packaged backend's runtime Maven coordinates.

Only public package names and versions leave the machine. Requires httpx.
This complements tests; it is not an independent security assessment.
"""
import io
import json
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
import httpx

root = Path(__file__).resolve().parents[1]
coordinates = set()
unidentified = []
resolved = {}
dependency_list = root / ".local/maven-dependencies.txt"
if not dependency_list.exists():
    raise SystemExit("First run backend.ps1 with dependency:list, -DincludeScope=runtime and -DoutputFile=../.local/maven-dependencies.txt")
for line in dependency_list.read_text().splitlines():
    parts = line.strip().split(":")
    if len(parts) in (5, 6) and parts[2] == "jar" and parts[-1].split()[0] in {"compile", "runtime"}:
        group, artifact, version = parts[0], parts[1], parts[-2]
        classifier = "-" + parts[3] if len(parts) == 6 else ""
        resolved[f"{artifact}-{version}{classifier}.jar"] = (f"{group}:{artifact}", version)
# Spring Boot's packager adds its launcher tools outside dependency:list.
for coordinate, version in list(resolved.values()):
    if coordinate == "org.springframework.boot:spring-boot":
        resolved[f"spring-boot-jarmode-tools-{version}.jar"] = ("org.springframework.boot:spring-boot-jarmode-tools", version)
with zipfile.ZipFile(root / "backend/target/cashflow-1.0.0.jar") as application:
    for name in application.namelist():
        if not name.startswith("BOOT-INF/lib/") or not name.endswith(".jar"):
            continue
        found = False
        with zipfile.ZipFile(io.BytesIO(application.read(name))) as dependency:
            for entry in dependency.namelist():
                if entry.startswith("META-INF/maven/") and entry.endswith("/pom.properties"):
                    properties = dict(line.split("=", 1) for line in dependency.read(entry).decode().splitlines() if "=" in line and not line.startswith("#"))
                    if {"groupId", "artifactId", "version"} <= properties.keys():
                        coordinates.add((properties["groupId"] + ":" + properties["artifactId"], properties["version"]))
                        found = True
        if not found:
            filename = name.rsplit("/", 1)[-1]
            if filename in resolved:
                coordinates.add(resolved[filename])
            else:
                unidentified.append(filename)
packages = sorted(coordinates)
with httpx.Client(timeout=45) as client:
    response = client.post("https://api.osv.dev/v1/querybatch", json={"queries": [{"package": {"ecosystem": "Maven", "name": name}, "version": version} for name, version in packages]})
    response.raise_for_status()
results = response.json()["results"]
affected = [{"package": name, "version": version, "advisories": result["vulns"]} for (name, version), result in zip(packages, results, strict=True) if result.get("vulns")]
report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "checked": len(packages), "affected": affected, "unidentifiedJars": unidentified}
(root / ".local").mkdir(exist_ok=True)
(root / ".local/java-audit.json").write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
sys.exit(1 if affected else 0)

"""
Export the Bucks data, and the weather the app compares it against, for review by another AI.

Pulls the app's own files off the phone over adb (read-only: db.json and the weather caches it
already downloaded), so the export is exactly what the app is judging on. Writes, into
exports/<date>/:

  README.md          what every file and column is, and the app's matching rules
  photos.csv         one row per photo: when, camera, tag, strip reading, and the weather then
  visits.csv         one row per visit (same tag + camera within the visit window), the unit the app counts
  wind_summary.csv   visits per wind vs how often each wind blew at the cameras over the same days
  forecast.csv       next 16 days, hourly, per camera, both models (what the sits are ranked on)

Weather at a photo = the 15-minute NOAA HRRR row at that camera (Open-Meteo historical-forecast
API, model gfs_hrrr), the same source and resolution the app uses; hourly extras (pressure, cloud,
rain) from the app's hourly cache. Nothing is re-fetched from the internet.

Usage:  python -I tools/export_for_ai.py            (phone connected over adb)
        python -I tools/export_for_ai.py --from DIR (a folder holding files/bucks/... already pulled)
"""
import csv
import io
import json
import os
import subprocess
import sys
import tarfile
from collections import Counter
from datetime import date, datetime, timezone
from zoneinfo import ZoneInfo

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
PKG = "com.kairos"
ZONE = ZoneInfo("America/New_York")
CALM_MPH = 3.0     # HisWinds.CALM_MPH in engine/.../HuntForecast.kt
MATCH_DEG = 30.0   # HisWinds.MATCH_DEG
AGREE_DEG = 45.0   # WindCheck.AGREE_DEG
VISIT_MIN = 10     # default visit window (Bucks settings)
C16 = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW']
C8 = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW']


def compass(deg, pts):
    n = len(pts)
    return pts[int(((deg + 180 / n) % 360) // (360 / n))]


def pull(dest):
    """Copy the app's bucks folder off the phone (run-as works on this debug build)."""
    raw = subprocess.run([ADB, "exec-out", "run-as", PKG, "tar", "cf", "-", "files/bucks/db.json", "files/bucks/wx"],
                         capture_output=True, check=True).stdout
    with tarfile.open(fileobj=io.BytesIO(raw)) as t:
        t.extractall(dest, filter="data")


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "exports", date.today().isoformat())
    os.makedirs(out, exist_ok=True)
    src = sys.argv[sys.argv.index("--from") + 1] if "--from" in sys.argv else os.path.join(out, "raw")
    if "--from" not in sys.argv:
        pull(src)
    bucks = os.path.join(src, "files", "bucks")
    db = json.load(open(os.path.join(bucks, "db.json"), encoding="utf-8"))
    cams = {c["id"]: c for c in db["cameras"]}
    names = {b["id"]: b["name"] for b in db["bucks"]}

    # Weather caches, per camera.
    q15, hourly, fc = {}, {}, {}
    for cid in cams:
        p = os.path.join(bucks, "wx", f"{cid}_q15.json")
        if os.path.exists(p):
            rows = [r for d in json.load(open(p))["days"].values() for r in d["r"]]
            q15[cid] = {r[0]: r for r in rows}   # t, mph, dir, gust, tempF
        for key, store in (("", hourly), ("_fc16", fc)):
            p = os.path.join(bucks, "wx", f"{cid}{key}.json")
            if os.path.exists(p):
                store[cid] = {r[0]: r for r in json.load(open(p))["hours"]}
    # Hourly row: t, tempF, mph, dir, gust, pressure_hPa(sea level), cloud%, precip_in, source,
    #             feelsF, humidity%, precip_prob%, snow_in, code, ecmwf_dir, ecmwf_mph

    def wx_at(cid, epoch):
        q = q15.get(cid, {}).get(epoch - epoch % 900)
        h = hourly.get(cid, {}).get(epoch - epoch % 3600)
        return q, h

    def local(epoch):
        return datetime.fromtimestamp(epoch, timezone.utc).astimezone(ZONE)

    photos = []
    for p in db["photos"]:
        if not p.get("capture"):
            continue
        t = datetime.fromisoformat(p["capture"]).replace(tzinfo=ZoneInfo(p.get("zone") or "America/New_York"))
        photos.append((int(t.timestamp()), p))
    photos.sort(key=lambda x: x[0])

    # ---- photos.csv
    with open(os.path.join(out, "photos.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["photo_id", "file", "local_time", "camera", "camera_facing_deg", "tag", "bucks", "night_ir",
                    "time_source", "strip_text", "strip_tempF",
                    "hrrr15_wind_from_deg", "hrrr15_wind_from", "hrrr15_wind_mph", "hrrr15_gust_mph", "hrrr15_tempF",
                    "hourly_pressure_hPa", "hourly_cloud_pct", "hourly_precip_in"])
        for epoch, p in photos:
            cid = p.get("cam")
            q, h = wx_at(cid, epoch) if cid else (None, None)
            w.writerow([p["id"], p["src"], local(epoch).strftime("%Y-%m-%d %H:%M"), cams.get(cid, {}).get("name", ""),
                        round(cams[cid]["facing"]) if cid in cams and cams[cid].get("facing") is not None else "",
                        p.get("label", ""), "; ".join(names.get(b, b) for b in p.get("bucks") or []), p.get("night", ""),
                        p.get("timeSource", ""), p.get("strip", ""), p.get("stripTemp", ""),
                        q and q[2], q and q[2] is not None and compass(q[2], C16), q and q[1], q and q[3], q and q[4],
                        h and h[5], h and h[6], h and h[7]])

    # ---- visits.csv: same tag + same camera, photos within the visit window of the last one = one visit
    visits = []
    for epoch, p in photos:
        if not p.get("cam") or p.get("label") in (None, "UNSORTED", "NOTHING"):
            continue
        key = (p["cam"], p["label"], tuple(sorted(p.get("bucks") or [])))
        v = next((v for v in reversed(visits) if v["key"] == key and epoch - v["last"] <= VISIT_MIN * 60), None)
        if v:
            v["last"] = epoch; v["ids"].append(p["id"])
        else:
            visits.append({"key": key, "start": epoch, "last": epoch, "ids": [p["id"]]})
    winds = Counter()
    with open(os.path.join(out, "visits.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["start_local", "camera", "tag", "bucks", "photos", "hrrr15_wind_from_deg", "wind_mph", "wind_8pt", "tempF"])
        for v in sorted(visits, key=lambda v: v["start"]):
            cid, label, bk = v["key"]
            q, _ = wx_at(cid, v["start"])
            sector = "" if not q or q[1] is None else ("calm" if q[1] < CALM_MPH else compass(q[2], C8))
            if sector:
                winds[sector] += 1
            w.writerow([local(v["start"]).strftime("%Y-%m-%d %H:%M"), cams[cid]["name"], label, "; ".join(names.get(b, b) for b in bk),
                        len(v["ids"]), q and q[2], q and q[1], sector, q and q[4]])

    # ---- wind_summary.csv: visits per wind vs how often that wind blew (every 15 min, same days, all cameras)
    if visits:
        d0, d1 = local(visits[0]["start"]).date(), local(visits[-1]["start"]).date()
        base = Counter()
        for cid, rows in q15.items():
            for t, r in rows.items():
                if d0 <= local(t).date() <= d1 and r[1] is not None:
                    base["calm" if r[1] < CALM_MPH else compass(r[2], C8)] += 1
        nb, nv = sum(base.values()), sum(winds.values())
        with open(os.path.join(out, "wind_summary.csv"), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["wind", "visits", "share_of_visits_pct", "share_of_time_pct", f"period {d0} to {d1}"])
            for k in ["calm"] + C8:
                w.writerow([k, winds[k], round(100 * winds[k] / nv) if nv else 0, round(100 * base[k] / nb) if nb else 0])

    # ---- forecast.csv
    with open(os.path.join(out, "forecast.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["camera", "local_time", "tempF", "feelsF", "noaa_wind_from_deg", "noaa_wind_from", "noaa_mph", "gust_mph",
                    "ecmwf_wind_from_deg", "ecmwf_mph", "models", "pressure_hPa", "cloud_pct", "rain_chance_pct", "precip_in", "humidity_pct"])
        for cid, rows in fc.items():
            for t in sorted(rows):
                r = rows[t]
                m, d, am, ad = r[2], r[3], r[15] if len(r) > 15 else None, r[14] if len(r) > 14 else None
                if None in (m, d, am, ad):
                    check = "one model"
                elif m < 4 and am < 4:
                    check = "agree (both calm)"
                elif min(m, am) < CALM_MPH:
                    check = "DISAGREE (one calm)"
                else:
                    gap = abs((d - ad + 180) % 360 - 180)
                    check = "agree" if gap <= AGREE_DEG else f"DISAGREE ({round(gap)} deg apart)"
                w.writerow([cams[cid]["name"], local(t).strftime("%Y-%m-%d %H:%M"), r[1], r[9], d, d is not None and compass(d, C16), m, r[4],
                            ad, am, check, r[5], r[6], r[11], r[7], r[10]])

    with open(os.path.join(out, "README.md"), "w", encoding="utf-8") as f:
        f.write(README.format(n_photos=len(photos), n_visits=len(visits),
                              cams=", ".join(f"{c['name']} ({c['lat']:.5f}, {c['lon']:.5f}, facing {round(c['facing'])} deg)" for c in cams.values())))
    print("wrote", out)


README = """# Kairos trail-camera export

Southern Maine, two trail cameras: {cams}. {n_photos} photos, {n_visits} visits.
Exported from the Kairos app's own files, so this is exactly what the app compares.

## Files
- **photos.csv**: every photo. `tag` = BUCK / DOE / UNSURE; `bucks` = which buck (named bucks, or the
  "Young bucks" pool for spikes and small racks). `time_source` STRIP = the date/time was read off the
  camera's info strip by on-phone OCR (`strip_text` is the raw read; MANUAL = fixed by hand).
  `strip_tempF` is the camera's own thermometer. `night_ir` = infrared (night) photo.
  `hrrr15_*` = NOAA HRRR model weather at the camera at that 15-minute step. Wind direction is where
  the wind blows FROM (meteorological), degrees true.
- **visits.csv**: the unit the app counts. One visit = photos with the same tag and the same buck(s)
  at the same camera, each within {visit} minutes of the previous one. Weather = at the visit's start.
- **wind_summary.csv**: visits per wind (8-point, calm under {calm:g} mph) next to how often that wind
  actually blew at the cameras over the same days (every 15-min step). A wind with a bigger share of
  visits than of time is one deer showed up on more than chance.
- **forecast.csv**: the next 16 days, hourly. NOAA = Open-Meteo best match (HRRR for about 2 days,
  then GFS). ECMWF IFS is a second, independent model. `models` = whether they agree.

## The app's rules (working definitions, not published thresholds)
- A forecast hour is "a buck's wind" when it blows from within {match:g} deg of a wind he visited on,
  or both are calm (under {calm:g} mph).
- The two models agree when both are under 4 mph, or neither is calm and they are within {agree:g} deg.
  When they disagree the hour earns no points; the app shows both readings and says to check.
- A sit = the first or last 3 hours of shooting light (30 min before sunrise / after sunset).
- Mentor weather calls (cold front, 10-15 F+ drop in the 24-h mean, 32-55 F evening, light rain,
  wind dropping 7+ mph) come from the user's YouTube transcript library, weighted by how many sources agree.

## Known limits
- Weather is a model value for a ~3 km grid cell, not a measurement at the stand.
- Which way each deer was walking is not recorded.
- One photo (Spypoint_20261008_011803.jpg) has a misread date: the strip says 10/07/2026, stored as 10/01.
""".replace("{visit}", str(VISIT_MIN)).replace("{calm:g}", f"{CALM_MPH:g}").replace("{match:g}", f"{MATCH_DEG:g}").replace("{agree:g}", f"{AGREE_DEG:g}")

if __name__ == "__main__":
    main()

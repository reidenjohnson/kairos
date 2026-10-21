"""Build a terrain wind pack (.kwp) for one spot with WindNinja, for the app's Wind screen.

HRRR (3 km, via Open-Meteo) gives the wind for the area. WindNinja bends that wind over the real
ground (USGS 3DEP lidar elevation, 10 m) and adds day/night slope thermals, one run per hour, from
7 days back to 48 hours ahead. The result is one file the phone reads; see FORMAT below.

Usage:
  python tools/wind_pack.py --name "Buck camera" --lat 43.8500 --lon -70.5800
  python tools/wind_pack.py --name "Buck camera" --lat 43.8500 --lon -70.5800 --push

Options: --radius-mi (half-width of the square, default 1.5), --mesh-m (WindNinja cell size,
default 30), --vegetation (grass|brush|trees, default trees), --past-hours (168),
--future-hours (48), --jobs (parallel WindNinja processes, default cores/2), --push (copy the pack
to the phone over adb; needs a debug build of the app installed).

Needs: WindNinja 4.0 installed in tools/windninja/app (gitignored; see notes/WIND.md), numpy,
tifffile, pyproj, curl on PATH. Runs are cached in tools/windninja/cache/, so a refresh only
re-solves hours whose inputs changed (mostly the forecast hours).

FORMAT (little-endian), shared with app/src/main/java/com/kairos/data/WindPack.kt:
  "KWP1" | int32 headerLen | header JSON (UTF-8) |
  per hour, in header.hours order: int16[nrows*ncols] u, then int16[nrows*ncols] v
  Row 0 = north, col 0 = west. Values = mph * 100; u = east, v = north (the way the wind blows
  TOWARD); -32768 = no data. Header: name, centerLat, centerLon, west, south, east, north (outer
  cell edges, EPSG:4326), ncols, nrows, hours (unix s UTC, ascending), meshMeters, source,
  createdUtc, elev (base64 int16 meters, same grid).
"""
import argparse
import base64
import hashlib
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import numpy as np
import tifffile
from pyproj import Transformer

ROOT = Path(__file__).resolve().parent.parent
WN_DIR = ROOT / "tools" / "windninja"
WN_CLI = WN_DIR / "app" / "bin" / "WindNinja_cli.exe"
CACHE = WN_DIR / "cache"
PACKS = WN_DIR / "packs"

DEM_URL = "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/exportImage"
METEO_URL = "https://api.open-meteo.com/v1/forecast"
DEM_RES_M = 10
PAD = 1.3            # WindNinja domain = pack box * PAD; its edges are boundary-affected, so they're cut
NODATA = -32768
APP_ID = "com.kairos"


def log(msg):
    print(msg, flush=True)


def curl(url, out=None, tries=4):
    """curl, not urllib: Python's TLS handshake to these hosts gets reset on this machine."""
    for i in range(tries):
        args = ["curl", "-s", "-f", "-m", "180", url]
        if out:
            args += ["-o", str(out)]
        r = subprocess.run(args, capture_output=True)
        if r.returncode == 0:
            return r.stdout
        time.sleep(2 * (i + 1))
    raise RuntimeError(f"download failed after {tries} tries: {url[:120]}")


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def utm_epsg(lat, lon):
    return (32600 if lat >= 0 else 32700) + int((lon + 180) // 6) + 1


# ---------------------------------------------------------------- inputs

def fetch_dem(path, epsg, cx, cy, half_m):
    """USGS 3DEP bare-earth elevation (lidar where flown) as a UTM GeoTIFF. SRTM, WindNinja's
    default, measures the tops of the trees in forest, which invents ridges that aren't there."""
    if path.exists():
        return
    n = int(round(2 * half_m / DEM_RES_M))
    q = dict(bbox=f"{cx - half_m},{cy - half_m},{cx + half_m},{cy + half_m}", bboxSR=epsg, imageSR=epsg,
             size=f"{n},{n}", format="tiff", pixelType="F32", interpolation="RSP_BilinearInterpolation",
             noData=-9999, f="image")
    tmp = path.with_suffix(".part")
    curl(DEM_URL + "?" + urllib.parse.urlencode(q), tmp)
    a = tifffile.imread(tmp)
    if a.shape != (n, n) or np.nanmin(a) <= -9000:
        tmp.unlink()
        raise RuntimeError("3DEP returned an incomplete elevation grid (no coverage here?)")
    tmp.replace(path)


def fetch_hrrr(lat, lon, past_hours, future_hours):
    """Hourly HRRR at the center: the domain-average wind WindNinja starts from, plus the air
    temperature and cloud cover its thermal model needs. Returns (tz name, rows)."""
    q = dict(latitude=f"{lat:.5f}", longitude=f"{lon:.5f}", models="gfs_hrrr", timezone="auto",
             timeformat="unixtime", wind_speed_unit="mph", temperature_unit="fahrenheit",
             hourly="wind_speed_10m,wind_direction_10m,temperature_2m,cloud_cover",
             past_days=math.ceil(past_hours / 24) + 1, forecast_days=math.ceil(future_hours / 24) + 1)
    d = json.loads(curl(METEO_URL + "?" + urllib.parse.urlencode(q)))
    h = d["hourly"]
    now = time.time()
    lo, hi = now - past_hours * 3600, now + future_hours * 3600
    rows = []
    for i, t in enumerate(h["time"]):
        vals = [h[k][i] for k in ("wind_speed_10m", "wind_direction_10m", "temperature_2m", "cloud_cover")]
        if lo - 3600 < t <= hi and None not in vals:
            rows.append(dict(t=t, speed=vals[0], dir=vals[1], temp=vals[2], cloud=vals[3]))
    if len(rows) < (past_hours + future_hours) * 0.9:
        raise RuntimeError(f"Open-Meteo returned only {len(rows)} usable hours")
    return d["timezone"], rows


# ---------------------------------------------------------------- WindNinja

def run_hour(row, dem, tz, mesh, veg, out_root):
    """One WindNinja run: HRRR's wind for this hour as the domain average, diurnal thermals on.
    Cached by the rounded inputs, so an unchanged hour is never solved twice."""
    local = datetime.fromtimestamp(row["t"], ZoneInfo(tz))
    inputs = (round(row["speed"], 1), round(row["dir"]), round(row["temp"]), round(row["cloud"] / 5) * 5)
    key = hashlib.sha1(repr((row["t"], inputs, mesh, veg)).encode()).hexdigest()[:10]
    out = out_root / f"{row['t']}_{key}"
    done = out / "done.json"
    if done.exists():
        return row["t"], out, True
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    args = [str(WN_CLI), "--num_threads", "2", "--elevation_file", str(dem),
            "--initialization_method", "domainAverageInitialization", "--time_zone", tz,
            "--input_speed", str(max(inputs[0], 0.1)), "--input_speed_units", "mph",
            "--input_direction", str(inputs[1] % 360),
            "--input_wind_height", "10", "--units_input_wind_height", "m",
            "--output_wind_height", "10", "--units_output_wind_height", "m",
            "--vegetation", veg, "--diurnal_winds", "true",
            "--year", str(local.year), "--month", str(local.month), "--day", str(local.day),
            "--hour", str(local.hour), "--minute", str(local.minute),
            "--uni_air_temp", str(inputs[2]), "--air_temp_units", "F",
            "--uni_cloud_cover", str(inputs[3]), "--cloud_cover_units", "percent",
            "--mesh_resolution", str(mesh), "--units_mesh_resolution", "m",
            "--write_ascii_output", "true", "--ascii_out_uv", "true", "--output_speed_units", "mph",
            "--output_path", str(out)]
    r = subprocess.run(args, capture_output=True, text=True)
    if r.returncode != 0 or not list(out.glob("*_u.asc")):
        raise RuntimeError(f"WindNinja failed for {local:%Y-%m-%d %H:%M}:\n{(r.stdout + r.stderr)[-1500:]}")
    done.write_text(json.dumps(dict(inputs=inputs, local=local.isoformat())))
    return row["t"], out, False


def read_asc(path):
    with open(path) as f:
        hdr = {}
        for _ in range(6):
            k, v = f.readline().split()
            hdr[k.lower()] = float(v)
        a = np.loadtxt(f)
    a[a == hdr["nodata_value"]] = np.nan
    return hdr, a


# ---------------------------------------------------------------- resampling

class Resampler:
    """Maps WindNinja's UTM grid onto the pack's lat/lon grid. WindNinja's u/v point to UTM grid
    north, which is up to ~2 degrees off true north away from the zone's center line, so each
    vector is turned by the local grid convergence on the way out."""

    def __init__(self, hdr, epsg, box, cell_m):
        west, south, east, north = box
        lat0 = (south + north) / 2
        dlat = cell_m / 111_320
        dlon = cell_m / (111_320 * math.cos(math.radians(lat0)))
        self.ncols = int(round((east - west) / dlon))
        self.nrows = int(round((north - south) / dlat))
        self.box = (west, south, west + self.ncols * dlon, south + self.nrows * dlat)
        lons = west + (np.arange(self.ncols) + 0.5) * dlon
        lats = self.box[3] - (np.arange(self.nrows) + 0.5) * dlat
        LON, LAT = np.meshgrid(lons, lats)
        fwd = Transformer.from_crs(4326, epsg, always_xy=True)
        inv = Transformer.from_crs(epsg, 4326, always_xy=True)
        X, Y = fwd.transform(LON, LAT)
        cs = hdr["cellsize"]
        nr = int(hdr["nrows"])
        # fractional (row, col) into the WindNinja grid, cell centers at integer positions
        self.c = (X - hdr["xllcorner"]) / cs - 0.5
        self.r = (nr - 0.5) - (Y - hdr["yllcorner"]) / cs
        # true bearing of grid north at each output cell
        lon2, lat2 = inv.transform(X, Y + 50.0)
        gamma = np.arctan2((lon2 - LON) * np.cos(np.radians(LAT)), lat2 - LAT)
        self.cos, self.sin = np.cos(gamma), np.sin(gamma)
        self.LAT, self.LON = LAT, LON

    def sample(self, a):
        """Bilinear sample of a WindNinja grid at the output cells (NaN outside)."""
        nr, nc = a.shape
        r0 = np.floor(self.r).astype(int)
        c0 = np.floor(self.c).astype(int)
        fr, fc = self.r - r0, self.c - c0
        ok = (r0 >= 0) & (c0 >= 0) & (r0 + 1 < nr) & (c0 + 1 < nc)
        r0c, c0c = r0.clip(0, nr - 2), c0.clip(0, nc - 2)
        v = (a[r0c, c0c] * (1 - fr) * (1 - fc) + a[r0c, c0c + 1] * (1 - fr) * fc
             + a[r0c + 1, c0c] * fr * (1 - fc) + a[r0c + 1, c0c + 1] * fr * fc)
        return np.where(ok, v, np.nan)

    def wind(self, u, v):
        ug, vg = self.sample(u), self.sample(v)
        # rotate from grid axes to true east/north
        return ug * self.cos + vg * self.sin, -ug * self.sin + vg * self.cos


def to_i16(a, scale):
    q = np.round(a * scale)
    q = np.where(np.isfinite(q), q.clip(-32767, 32767), NODATA)
    return q.astype("<i2")


# ---------------------------------------------------------------- main

def build(args):
    if not WN_CLI.exists():
        sys.exit(f"WindNinja not found at {WN_CLI}. See notes/WIND.md for the install step.")
    lat, lon = args.lat, args.lon
    epsg = utm_epsg(lat, lon)
    fwd = Transformer.from_crs(4326, epsg, always_xy=True)
    inv = Transformer.from_crs(epsg, 4326, always_xy=True)
    cx, cy = fwd.transform(lon, lat)
    half = args.radius_mi * 1609.344
    area = CACHE / f"{lat:.4f}_{lon:.4f}_{args.radius_mi:g}mi"
    area.mkdir(parents=True, exist_ok=True)

    dem = area / "dem3dep.tif"
    log(f"Elevation: USGS 3DEP {DEM_RES_M} m ...")
    fetch_dem(dem, epsg, cx, cy, half * PAD)
    log(f"Weather: HRRR via Open-Meteo, {args.past_hours} h back to {args.future_hours} h ahead ...")
    tz, rows = fetch_hrrr(lat, lon, args.past_hours, args.future_hours)

    runs = area / f"runs_{args.mesh_m}m_{args.vegetation}"
    log(f"WindNinja: {len(rows)} hours, {args.jobs} at a time ...")
    t0, results, cached = time.time(), {}, 0
    with ThreadPoolExecutor(args.jobs) as pool:
        futs = [pool.submit(run_hour, r, dem, tz, args.mesh_m, args.vegetation, runs) for r in rows]
        for i, f in enumerate(as_completed(futs), 1):
            t, out, hit = f.result()
            results[t] = out
            cached += hit
            if i % 24 == 0 or i == len(futs):
                log(f"  {i}/{len(futs)} ({cached} cached, {time.time() - t0:.0f} s)")

    # pack box: the requested square, in lat/lon
    corners = [inv.transform(cx + sx * half, cy + sy * half) for sx in (-1, 1) for sy in (-1, 1)]
    box = (max(c[0] for c in corners if c[0] < lon), max(c[1] for c in corners if c[1] < lat),
           min(c[0] for c in corners if c[0] > lon), min(c[1] for c in corners if c[1] > lat))
    hours = sorted(results)
    first = next(results[hours[0]].glob("*_u.asc"))
    hdr, _ = read_asc(first)
    rs = Resampler(hdr, epsg, box, args.mesh_m)

    # elevation on the pack grid, for the app (and for a sanity check here)
    p = tifffile.TiffFile(dem).pages[0]
    z = p.asarray().astype(float)
    x0, y0 = p.tags["ModelTiepointTag"].value[3:5]
    X, Y = fwd.transform(rs.LON, rs.LAT)
    zr = ((y0 - Y) / DEM_RES_M).astype(int).clip(0, z.shape[0] - 1)
    zc = ((X - x0) / DEM_RES_M).astype(int).clip(0, z.shape[1] - 1)
    elev = z[zr, zc]

    body, speeds = [], []
    for t in hours:
        stem = str(next(results[t].glob("*_u.asc")))[:-6]
        h2, u = read_asc(stem + "_u.asc")
        _, v = read_asc(stem + "_v.asc")
        if (h2["ncols"], h2["nrows"], h2["xllcorner"], h2["yllcorner"]) != (hdr["ncols"], hdr["nrows"], hdr["xllcorner"], hdr["yllcorner"]):
            raise RuntimeError(f"WindNinja grid changed between hours ({stem})")
        ue, vn = rs.wind(u, v)
        if np.isnan(ue).mean() > 0.01:
            raise RuntimeError(f"{np.isnan(ue).mean():.1%} of the pack has no wind at {t}: domain too small?")
        body += [to_i16(ue, 100).tobytes(), to_i16(vn, 100).tobytes()]
        speeds.append(np.nanmean(np.hypot(ue, vn)))

    header = dict(
        name=args.name, centerLat=lat, centerLon=lon,
        west=rs.box[0], south=rs.box[1], east=rs.box[2], north=rs.box[3],
        ncols=rs.ncols, nrows=rs.nrows, hours=[int(t) for t in hours], meshMeters=args.mesh_m,
        source=f"WindNinja 4.0 (mass-conserving + diurnal slope flows, {args.vegetation}) on USGS 3DEP "
               f"{DEM_RES_M} m elevation, started from NOAA HRRR via Open-Meteo",
        createdUtc=int(time.time()), timeZone=tz,
        elev=base64.b64encode(to_i16(elev, 1).tobytes()).decode(),
    )
    hb = json.dumps(header, separators=(",", ":")).encode()
    PACKS.mkdir(parents=True, exist_ok=True)
    out = PACKS / f"{slug(args.name)}.kwp"
    with open(out.with_suffix(".part"), "wb") as f:
        f.write(b"KWP1" + struct.pack("<i", len(hb)) + hb)
        for b in body:
            f.write(b)
    out.with_suffix(".part").replace(out)
    log(f"Wrote {out.relative_to(ROOT)}: {rs.ncols}x{rs.nrows} cells, {len(hours)} hours, "
        f"{out.stat().st_size / 1e6:.1f} MB, elevation {np.nanmin(elev):.0f}-{np.nanmax(elev):.0f} m, "
        f"mean wind {min(speeds):.1f}-{max(speeds):.1f} mph")
    return out


def push(pack):
    """Copy the pack into the app's private files/windpacks/ (debug builds allow run-as)."""
    adb = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
    adb = str(adb) if adb.exists() else "adb"
    tmp = f"/data/local/tmp/{pack.name}"
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    subprocess.run([adb, "push", str(pack), tmp], check=True, env=env)
    subprocess.run([adb, "shell", f"run-as {APP_ID} sh -c 'mkdir -p files/windpacks && cp {tmp} files/windpacks/{pack.name}' && rm {tmp}"],
                   check=True, env=env)
    log(f"Pushed to the phone: files/windpacks/{pack.name}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--name", required=True)
    ap.add_argument("--lat", type=float, required=True)
    ap.add_argument("--lon", type=float, required=True)
    ap.add_argument("--radius-mi", type=float, default=1.5)
    ap.add_argument("--mesh-m", type=int, default=30)
    ap.add_argument("--vegetation", choices=["grass", "brush", "trees"], default="trees")
    ap.add_argument("--past-hours", type=int, default=168)
    ap.add_argument("--future-hours", type=int, default=48)
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    ap.add_argument("--push", action="store_true")
    args = ap.parse_args()
    pack = build(args)
    if args.push:
        push(pack)


if __name__ == "__main__":
    main()

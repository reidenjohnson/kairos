"""Kairos — evidence-weighted "go hunt / go fish now" engine.

Pulls LIVE free weather for Sebago Lake, southern Maine (Open-Meteo, no API key),
computes sun + moon, and scores each species using weights derived from research.
Every factor and weight is documented with sources in SOURCES.md.

This is the BRAIN. The Watch 7 / phone app is a face we bolt on later.
"""

from __future__ import annotations

import json
import urllib.request
from datetime import date, datetime

from astral import LocationInfo, moon
from astral.sun import sun

# Sebago Lake, southern Maine
LAT, LON = 43.85, -70.56
TZ = "America/New_York"
LOC = LocationInfo("Sebago", "Maine", TZ, LAT, LON)

# Approx Sebago surface water temp by month (°F) — deep coldwater lake.
# Tier-3 proxy: fish respond to WATER temp, but the free feed only gives AIR temp.
# See SOURCES.md "Known limitation". Future fix: real lake-temp input.
SEBAGO_WATER_F = {1: 34, 2: 33, 3: 36, 4: 45, 5: 55, 6: 66,
                  7: 73, 8: 74, 9: 68, 10: 57, 11: 47, 12: 39}


# ----------------------------- data in -----------------------------

def fetch_weather() -> dict:
    url = (
        "https://api.open-meteo.com/v1/forecast"
        f"?latitude={LAT}&longitude={LON}"
        "&hourly=temperature_2m,surface_pressure"
        "&current=temperature_2m,surface_pressure,wind_speed_10m,cloud_cover"
        f"&timezone={TZ}&past_days=1&forecast_days=2"
        "&temperature_unit=fahrenheit&wind_speed_unit=mph"
    )
    with urllib.request.urlopen(url, timeout=20) as r:
        return json.load(r)


def clamp(x: float) -> float:
    return max(0.0, min(1.0, x))


# ----------------------- factor computations -----------------------

def pressure_trend_inhg(w: dict) -> float:
    """Pressure change over the last ~6h, inHg. Negative = falling (good)."""
    times, pres = w["hourly"]["time"], w["hourly"]["surface_pressure"]
    now = datetime.now().strftime("%Y-%m-%dT%H:00")
    i = times.index(now) if now in times else len(times) // 2
    return (pres[i] - pres[max(0, i - 6)]) * 0.02953  # hPa -> inHg


def temp_drop_next_24h(w: dict) -> float:
    """How much colder over the next 24h, °F. Positive = a cold front coming."""
    times, temps = w["hourly"]["time"], w["hourly"]["temperature_2m"]
    now = datetime.now().strftime("%Y-%m-%dT%H:00")
    i = times.index(now) if now in times else 0
    window = temps[i:i + 24] or temps[i:]
    return temps[i] - min(window)


def f_trend(ptrend: float) -> float:
    return clamp(0.5 + (-ptrend) / 0.10 * 0.5)  # -0.10 inHg/6h = strong fall


def f_range(inhg: float) -> float:
    # Whitetail feeding peaks 29.9-30.3 (Thomas); fish comfort 29.7-30.4.
    return clamp(1 - abs(inhg - 30.05) / 0.55)


def f_front(tdrop: float) -> float:
    return clamp(tdrop / 12)  # ~12°F drop coming = full-strength trigger


def f_cloud(cloud_pct: float) -> float:
    return clamp(cloud_pct / 100)


def f_temp(temp_f: float, kind: str, lo: float = 0, hi: float = 0,
           ideal: float = 0, spread: float = 1) -> float:
    if kind == "cold":                       # cold-loving game
        return clamp((hi - temp_f) / (hi - lo))
    if kind == "band":                       # warmwater fish, peak at ideal
        return clamp(1 - abs(temp_f - ideal) / spread)
    if kind == "coldwater":                  # salmon/togue/brookie: fine when cool
        if temp_f <= 62:
            return 1.0
        if temp_f >= 75:
            return 0.05
        return clamp(1 - (temp_f - 62) / 13)
    return 0.5


def f_wind(mph: float, lo: float, hi: float, hard: float) -> float:
    if lo <= mph <= hi:
        return 1.0
    if mph < lo:
        return clamp(0.5 + mph / (lo * 2)) if lo else 1.0
    return clamp(1 - (mph - hi) / (hard - hi))


def f_moon(illum: float, mode: str) -> float:
    # illum: 0 = new, 1 = full
    if mode == "inverse":         # snowshoe hare: bright moon = worse
        return clamp(1 - illum)
    if mode == "newfull":         # walleye: peaks near new AND full
        return clamp(abs(illum - 0.5) * 2)
    return 0.0                     # negligible for everything else


def moon_illum(d: date) -> tuple[float, str]:
    ph = moon.phase(d)  # 0=new .. 14=full .. 28=new
    illum = ph / 14 if ph <= 14 else (28 - ph) / 14
    name = ("new", "waxing crescent", "first quarter", "waxing gibbous",
            "full", "waning gibbous", "last quarter", "waning crescent")[int((ph % 28) / 3.5)]
    return illum, name


# --------------------------- species set ---------------------------
# Weights sum to 1.0 and come straight from the SOURCES.md table.

def _temp_args(kind, **kw):
    return {"kind": kind, **kw}

SPECIES = {
    "Whitetail deer": dict(kind="hunt", w=dict(temp=.32, trend=.10, rng=.15, front=.26, wind=.12, cloud=.05, moon=0),
                           temp=_temp_args("cold", lo=40, hi=78), wind=(3, 12, 25), moon="none"),
    "Moose": dict(kind="hunt", w=dict(temp=.47, trend=.10, rng=.05, front=.20, wind=.12, cloud=.06, moon=0),
                  temp=_temp_args("cold", lo=40, hi=72), wind=(2, 10, 22), moon="none"),
    "Elk": dict(kind="hunt", w=dict(temp=.40, trend=.10, rng=.10, front=.21, wind=.12, cloud=.07, moon=0),
                temp=_temp_args("cold", lo=40, hi=78), wind=(3, 12, 25), moon="none"),
    "Black bear": dict(kind="hunt", w=dict(temp=.35, trend=.15, rng=.05, front=.28, wind=.07, cloud=.10, moon=0),
                       temp=_temp_args("cold", lo=40, hi=75), wind=(2, 10, 22), moon="none"),
    "Snowshoe hare": dict(kind="hunt", w=dict(temp=.30, trend=.10, rng=.05, front=.10, wind=.20, cloud=.05, moon=.20),
                          temp=_temp_args("cold", lo=20, hi=55), wind=(0, 8, 18), moon="inverse"),
    "Upland (grouse/woodcock)": dict(kind="hunt", w=dict(temp=.35, trend=.10, rng=.05, front=.10, wind=.25, cloud=.15, moon=0),
                                     temp=_temp_args("cold", lo=25, hi=68), wind=(0, 7, 18), moon="none"),
    "Waterfowl (ducks)": dict(kind="hunt", w=dict(temp=.13, trend=.18, rng=.02, front=.32, wind=.30, cloud=.05, moon=0),
                              temp=_temp_args("cold", lo=25, hi=70), wind=(8, 16, 30), moon="none"),
    "Largemouth bass": dict(kind="fish", w=dict(temp=.23, trend=.40, rng=.20, front=.08, wind=.05, cloud=.04, moon=0),
                            temp=_temp_args("band", ideal=80, spread=22), wind=(2, 12, 25), moon="none"),
    "Smallmouth bass": dict(kind="fish", w=dict(temp=.23, trend=.40, rng=.20, front=.08, wind=.05, cloud=.04, moon=0),
                            temp=_temp_args("band", ideal=72, spread=16), wind=(2, 12, 25), moon="none"),
    "Salmon / togue / brookie": dict(kind="fish", w=dict(temp=.42, trend=.28, rng=.10, front=.10, wind=.05, cloud=.05, moon=0),
                                     temp=_temp_args("coldwater"), wind=(0, 10, 22), moon="none"),
    "Walleye": dict(kind="fish", w=dict(temp=.18, trend=.18, rng=.07, front=.08, wind=.22, cloud=.22, moon=.05),
                    temp=_temp_args("band", ideal=68, spread=18), wind=(6, 16, 30), moon="newfull"),
}


def main() -> None:
    wx = fetch_weather()
    cur = wx["current"]
    air_f = cur["temperature_2m"]
    pres_inhg = cur["surface_pressure"] * 0.02953
    wind_mph = cur["wind_speed_10m"]
    cloud_pct = cur["cloud_cover"]
    ptrend = pressure_trend_inhg(wx)
    tdrop = temp_drop_next_24h(wx)
    illum, mname = moon_illum(date.today())
    water_f = SEBAGO_WATER_F[date.today().month]
    s = sun(LOC.observer, date=date.today(), tzinfo=LOC.timezone)

    hdr = f"  Kairos — Sebago Lake, ME   {date.today():%A, %b %d %Y}"
    print("=" * 62)
    print(hdr)
    print("=" * 62)
    print(f"  Air {air_f:.0f}°F | water ~{water_f}°F | wind {wind_mph:.0f} mph | "
          f"{cloud_pct:.0f}% cloud | {pres_inhg:.2f} inHg")
    trend_word = "falling" if ptrend < -0.01 else "rising" if ptrend > 0.01 else "steady"
    print(f"  Pressure {trend_word} ({ptrend:+.2f}/6h) | front −{tdrop:.0f}°F/24h | "
          f"moon {mname}")
    print(f"  Sunrise {s['sunrise']:%H:%M}  Sunset {s['sunset']:%H:%M}   "
          f"(best windows: dawn & dusk)")
    print("-" * 62)

    rows = []
    for name, sp in SPECIES.items():
        w = sp["w"]
        temp_in = water_f if sp["kind"] == "fish" else air_f
        total = (
            w["temp"] * f_temp(temp_in, **sp["temp"])
            + w["trend"] * f_trend(ptrend)
            + w["rng"] * f_range(pres_inhg)
            + w["front"] * f_front(tdrop)
            + w["wind"] * f_wind(wind_mph, *sp["wind"])
            + w["cloud"] * f_cloud(cloud_pct)
            + w["moon"] * f_moon(illum, sp["moon"])
        )
        rows.append((sp["kind"], name, round(total * 100)))

    def rating(v):
        return "Prime" if v >= 75 else "Good" if v >= 55 else "Fair" if v >= 40 else "Slow"

    for side, label in (("hunt", "HUNT"), ("fish", "FISH")):
        print(f"  --- {label} ---")
        for kind, name, v in sorted([r for r in rows if r[0] == side], key=lambda r: -r[2]):
            bar = "#" * (v // 5)
            print(f"  {name:26} {v:3d}  {rating(v):5} {bar}")
    print("=" * 62)


if __name__ == "__main__":
    main()

"""Build the engine's bundled NOAA station table for local normals.

Reads notes/normals_test/loo/*.json (made by notes/normals_test/loo.py: each station's NOAA
1991-2020 daily normals plus the Open-Meteo 30-yr modeled normal at its lat/lon) and writes
engine/src/main/resources/com/kairos/engine/station_normal_bias.tsv:

    id  name  lat  lon  <365 low biases>  <365 high biases>

bias = NOAA normal - modeled normal, degF, Jan 1..Dec 31 (no Feb 29). The engine adds the
distance-weighted bias of nearby stations to a spot's own modeled normal (LocalNormals.kt).
"""
import glob, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "notes", "normals_test", "loo")
OUT = os.path.join(ROOT, "engine", "src", "main", "resources", "com", "kairos", "engine", "station_normal_bias.tsv")

rows = []
for f in sorted(glob.glob(os.path.join(SRC, "*.json"))):
    s = json.load(open(f))
    days = sorted(k for k in s["model"] if k != "02-29")
    if len(days) != 365 or any(d not in s["noaa"] for d in days):
        print("skip (incomplete):", s["name"])
        continue
    lo = [s["noaa"][d][0] - s["model"][d][0] for d in days]
    hi = [s["noaa"][d][1] - s["model"][d][1] for d in days]
    sid = os.path.basename(f)[:-5]
    rows.append("\t".join([sid, s["name"], f"{s['lat']:.4f}", f"{s['lon']:.4f}"] +
                          [f"{x:.1f}" for x in lo + hi]))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", newline="\n") as o:
    o.write("# NOAA 1991-2020 normal minus Open-Meteo 30-yr modeled normal, degF. "
            "Built by tools/build_normals_bias.py. Cols: id name lat lon, 365 low, 365 high.\n")
    o.write("\n".join(rows) + "\n")
print(len(rows), "stations ->", OUT, os.path.getsize(OUT) // 1024, "KB")

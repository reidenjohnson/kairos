"""Build the Learn screen's app asset from the lesson files.

notes/ is gitignored, so the app can't read the lessons from there. This packs the curriculum
order, every lesson, the merged entries they cite ("Read more"), and the videos behind those
entries ("References") into one JSON file the app ships with.

Usage: python tools/build_learn_asset.py
Writes: app/src/main/assets/learn/deer.json
"""
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
LEARN = ROOT / "notes" / "learn"
RULES = ROOT / "notes" / "rules"
OUT = ROOT / "app" / "src" / "main" / "assets" / "learn" / "deer.json"

# First line of a per-video rule file: "# <title> (<channel>, <n> min)"
HEADER = re.compile(r"^#\s*(?P<title>.+)\s+\((?P<channel>[^()]*?)(?:,\s*[\d.]+\s*(?:min|hr|h)[^()]*)?\)\s*$")


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def load_videos():
    """Video id -> {title, channel}, read from the per-video rule files' header lines."""
    videos = {}
    for f in sorted((RULES / "deer").glob("*.yaml")):
        first = f.read_text(encoding="utf-8").splitlines()[0]
        m = HEADER.match(first)
        if m:
            videos[f.stem] = {"title": m["title"].strip(), "channel": m["channel"].strip()}
        else:
            videos[f.stem] = {"title": first.lstrip("# ").strip(), "channel": ""}
    return videos


def load_entries():
    entries = {}
    for f in sorted((RULES / "merged").glob("*.yaml")):
        for e in yaml.safe_load(f.read_text(encoding="utf-8")) or []:
            entries[e["id"]] = e
    return entries


def load_member_index():
    """Per-video rule id -> the video file it lives in. Most ids start with the video id, but
    the pilot's ids don't, so index by file rather than trusting the prefix."""
    index = {}
    for f in sorted((RULES / "deer").glob("*.yaml")):
        for r in yaml.safe_load(f.read_text(encoding="utf-8")) or []:
            index[r["id"]] = f.stem
    return index


def main():
    videos = load_videos()
    entries = load_entries()
    member_video = load_member_index()
    curriculum = [u for u in yaml.safe_load((LEARN / "curriculum.yaml").read_text(encoding="utf-8")) if "unit" in u]
    files = {}
    for f in (LEARN / "lessons").glob("*.yaml"):
        doc = yaml.safe_load(f.read_text(encoding="utf-8"))
        files[doc["unit"]] = doc

    problems = []
    units_out, used_entries = [], set()
    for u in curriculum:
        doc = files.get(u["unit"])
        if doc is None:
            problems.append(f"no lesson file for unit '{u['unit']}'")
            continue
        lessons = []
        for i, l in enumerate(doc["lessons"], 1):
            lid = f"{slug(u['unit'])}-{i}"
            sections = []
            for s in l.get("sections", []):
                cites = s.get("from", []) or []
                for c in cites:
                    if c not in entries:
                        problems.append(f"{lid}: unknown entry {c}")
                used_entries.update(c for c in cites if c in entries)
                sections.append({"heading": s["heading"], "text": s["text"].strip(), "from": cites})
            lessons.append({
                "id": lid,
                "title": l["title"],
                "minutes": l.get("minutes", 5),
                "hook": (l.get("hook") or "").strip(),
                "sections": sections,
                "tryThis": (l.get("try_this") or "").strip(),
                "headsUp": (l.get("heads_up") or "").strip(),
                "check": [{"q": c["q"], "a": c["a"]} for c in l.get("check", [])],
            })
        units_out.append({"level": u["level"], "unit": u["unit"], "goal": u.get("goal", ""), "lessons": lessons})

    entries_out, used_videos = {}, set()
    for eid in sorted(used_entries):
        e = entries[eid]
        vids = []
        for m in e.get("members", []):
            v = member_video.get(m)
            if v and v not in vids:
                vids.append(v)
            elif not v:
                problems.append(f"{eid}: member {m} has no video file")
        used_videos.update(vids)
        entries_out[eid] = {
            "action": e.get("action", ""),
            "why": e.get("why", ""),
            "people": e.get("people", []),
            "agreement": e.get("agreement", len(e.get("people", []))),
            "videos": vids,
        }

    out = {
        "version": 1,
        "species": "deer",
        "units": units_out,
        "entries": entries_out,
        "videos": {v: videos[v] for v in sorted(used_videos)},
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    n_lessons = sum(len(u["lessons"]) for u in units_out)
    print(f"{len(units_out)} units, {n_lessons} lessons, {len(entries_out)} entries, "
          f"{len(out['videos'])} videos -> {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")
    for p in problems:
        print("PROBLEM:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

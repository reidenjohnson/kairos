"""Where every video stands in the pipeline, and what to do next.

Usage: python tools/kb_status.py

    transcript (notes/transcripts/<topic>/)  ->  rules (notes/rules/<topic>/<ID>.yaml)
    ->  merged (notes/rules/merged/)  ->  curriculum unit (tools/build_curriculum.py)
    ->  lesson key point (notes/learn/lessons/*.yaml)

Also accepts loose .txt transcripts dropped in ~/Downloads or notes/transcripts/inbox/ and says
whether each is a duplicate of a video already saved. See notes/rules/ADD_VIDEOS.md.
"""
import difflib
import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
T = ROOT / "notes" / "transcripts"
R = ROOT / "notes" / "rules"
L = ROOT / "notes" / "learn"
VID = re.compile(r"\[([\w-]{11})\]\.txt$")


def words(p):
    t = re.sub(r"\[\d+:\d+(:\d+)?\]", "", p.read_text(encoding="utf-8", errors="ignore").lower())
    return re.sub(r"[^a-z ]", " ", t).split()


def main():
    transcripts = {m.group(1): p for p in T.glob("*/*.txt") if (m := VID.search(p.name))}
    rule_files = {p.stem: p for p in R.glob("*/*.yaml") if p.parent.name not in ("merged", "proposals")}
    people = yaml.safe_load((R / "people.yaml").read_text(encoding="utf-8")) or {}

    rules = {}
    for p in rule_files.values():
        for r in yaml.safe_load(p.read_text(encoding="utf-8")) or []:
            rules[r["id"]] = r
    merged, member_of = {}, {}
    for p in (R / "merged").glob("*.yaml"):
        for e in yaml.safe_load(p.read_text(encoding="utf-8")) or []:
            merged[e["id"]] = e
            for m in e.get("members") or []:
                member_of[m] = e["id"]
    placed = set()
    cur = L / "curriculum.yaml"
    if cur.exists():
        for u in yaml.safe_load(cur.read_text(encoding="utf-8")) or []:
            placed.update(u.get("entries") or [])
    taught = set()
    for p in (L / "lessons").glob("*.yaml"):
        for les in (yaml.safe_load(p.read_text(encoding="utf-8")) or {}).get("lessons") or []:
            for k in les.get("sections") or []:
                taught.update(k.get("from") or [])

    todo = []
    no_rules = sorted(v for v in transcripts if v not in rule_files)
    if no_rules:
        todo.append(("1. Extract rules (EXTRACTION_GUIDE.md)", [transcripts[v].name for v in no_rules]))
    no_person = sorted(v for v in transcripts if v not in people)
    if no_person:
        todo.append(("2. Add to people.yaml (who is speaking, kind)", no_person))
    unmerged = sorted(i for i in rules if i not in member_of)
    if unmerged:
        todo.append(("3. Merge into merged/ (MERGE_GUIDE.md: join an existing entry or add one)", unmerged))
    unplaced = sorted(i for i in merged if i not in placed)
    if unplaced:
        todo.append(("4. Place in a curriculum unit (UNITS in tools/build_curriculum.py)", unplaced))
    lesson_units = {p.stem for p in (L / "lessons").glob("*.yaml")}
    untaught = sorted(i for i in placed if i not in taught)
    if untaught:
        todo.append((f"5. Teach in a lesson ({len(lesson_units)} unit(s) written so far)",
                     [f"{len(untaught)} placed entries not yet in any lesson key point"]))

    # Loose transcripts dropped in by hand: flag duplicates of saved videos.
    loose = list((T / "inbox").glob("*.txt")) + list((Path.home() / "Downloads").glob("*.txt"))
    saved = {v: words(p) for v, p in transcripts.items()} if loose else {}
    for p in loose:
        w = words(p)
        if len(w) < 200:
            continue
        best = max(saved.items(), key=lambda kv: difflib.SequenceMatcher(None, w[:600], kv[1][:600]).quick_ratio())
        ratio = difflib.SequenceMatcher(None, w, best[1], autojunk=False).ratio()
        if ratio > 0.8:
            print(f"= {p.name}: duplicate of {best[0]} ({ratio:.0%} same words), already in the system")
        else:
            todo.insert(0, ("0. New loose transcript: find its YouTube URL and pull it, or save it as "
                            "notes/transcripts/<topic>/<Channel> - <Title> [<ID>].txt", [p.name]))

    print(f"{len(transcripts)} transcripts, {len(rules)} rules, {len(merged)} merged, "
          f"{len(placed)} placed in curriculum, {len(taught & set(merged))} taught in lessons")
    if not todo:
        print("Nothing waiting.")
    for step, items in todo:
        print(f"\n{step}: {len(items)}")
        for i in items[:15]:
            print(f"   {i}")
        if len(items) > 15:
            print(f"   ... and {len(items) - 15} more")


if __name__ == "__main__":
    main()

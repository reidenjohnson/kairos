"""Check Learn lesson files against the curriculum and the style rules.

Usage: python tools/check_lessons.py [unit-file-stem ...]

Per unit file (notes/learn/lessons/<stem>.yaml): the unit exists in notes/learn/curriculum.yaml;
every curriculum entry of that unit is taught in some section's `from` (nothing lost); every `from`
id is a real merged entry (entries from other units may be cited too); each lesson has hook, 3-6 sections, try_this, check (2);
about 450-950 words; no em/en dashes; no hype words. See notes/learn/STYLE.md.
"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
LEARN = ROOT / "notes" / "learn"
HYPE = re.compile(r"\b(game[- ]changer|secret|deadly|ultimate|crush|insane)\b|!", re.I)


def stem(title):
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")


def main():
    cur = [u for u in yaml.safe_load((LEARN / "curriculum.yaml").read_text(encoding="utf-8")) if "unit" in u]
    units = {stem(u["unit"]): u for u in cur}
    known = {e for u in cur for e in u["entries"]}
    files = sys.argv[1:] or sorted(p.stem for p in (LEARN / "lessons").glob("*.yaml"))
    bad = 0
    for f in files:
        p = LEARN / "lessons" / f"{f}.yaml"
        errs = []
        if f not in units:
            print(f"x {f}: no curriculum unit with that name (expected one of the stems)")
            bad += 1
            continue
        want = set(units[f]["entries"])
        doc = yaml.safe_load(p.read_text(encoding="utf-8")) if p.exists() else None
        if not doc:
            print(f"x {f}: missing file")
            bad += 1
            continue
        taught = set()
        for les in doc.get("lessons") or []:
            t = les.get("title", "?")
            secs = les.get("sections") or []
            if not les.get("hook"):
                errs.append(f"{t}: no hook")
            if not 3 <= len(secs) <= 6:
                errs.append(f"{t}: {len(secs)} sections (want 3-6)")
            if not les.get("try_this"):
                errs.append(f"{t}: no try_this")
            if len(les.get("check") or []) != 2:
                errs.append(f"{t}: want 2 check questions")
            text = " ".join([les.get("hook", ""), les.get("try_this", ""), les.get("heads_up", "") or ""]
                            + [s.get("text", "") for s in secs]
                            + [c.get("q", "") + " " + c.get("a", "") for c in les.get("check") or []])
            n = len(text.split())
            if not 450 <= n <= 950:
                errs.append(f"{t}: {n} words (want about 450-950)")
            if "—" in text or "–" in text:
                errs.append(f"{t}: has an em/en dash")
            if HYPE.search(text):
                errs.append(f"{t}: hype word or exclamation: {HYPE.search(text).group(0)!r}")
            for s in secs:
                for i in s.get("from") or []:
                    if i not in known:
                        errs.append(f"{t}: from {i} is not a merged entry")
                    taught.add(i)
                if not s.get("from"):
                    errs.append(f"{t} / {s.get('heading')}: no from")
        for i in sorted(want - taught):
            errs.append(f"entry {i} is not taught in any section")
        for e in errs:
            print(f"x {f}: {e}")
        print(f"{f}: {len(doc.get('lessons') or [])} lessons, {len(taught & want)}/{len(want)} entries taught, "
              f"{len(errs)} problems")
        bad += len(errs)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()

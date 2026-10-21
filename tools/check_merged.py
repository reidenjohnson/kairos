"""Check the merged (consensus) rule layer against the per-video rules.

Usage: python tools/check_merged.py [job ...]

For each job (default: all jobs that have a merged file): every source rule of that job must be a
member of exactly one merged entry; every member must exist; agreement must equal the number of
distinct people behind the members (a rule's `speaker:`, else notes/rules/people.yaml); conditions must use the schema
vocabulary; ids must be unique. See notes/rules/MERGE_GUIDE.md.
"""
import glob
import re
import sys
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
RULES = ROOT / "notes" / "rules"


def person_of(rule, people):
    """Who teaches a rule: its own `speaker:` (a podcast host, a caller, a guest on a group show)
    when set, else the one person people.yaml gives for the whole video."""
    return persons_of(rule, people)[0]


def persons_of(rule, people):
    """Everyone who teaches a rule. `speaker:` may be one name or a list (a guest's point that
    the host also teaches from his own hunts); without it, people.yaml's person for the video."""
    sp = rule.get("speaker")
    if sp:
        return [sp] if isinstance(sp, str) else list(sp)
    return [people[rule["sources"][0]["video"]]["person"]]


def kind_of(rule, people):
    """The video's kind (research, hunter, ...) applies only when the video's own person is among
    the rule's speakers; a host's story inside a research video is a hunter's experience."""
    video = people[rule["sources"][0]["video"]]
    return video["kind"] if video["person"] in persons_of(rule, people) else "hunter"


def main():
    schema = yaml.safe_load((RULES / "rule_schema.yaml").read_text(encoding="utf-8"))
    vocab = dict(schema["conditions"])
    ranged = set(vocab.pop("ranged", []))
    people = yaml.safe_load((RULES / "people.yaml").read_text(encoding="utf-8"))

    source = {}
    for p in sorted((RULES / "deer").glob("*.yaml")):
        for r in yaml.safe_load(p.read_text(encoding="utf-8")) or []:
            source[r["id"]] = r

    mentors = yaml.safe_load((RULES / "mentors.yaml").read_text(encoding="utf-8")) or {}

    merged_files = sorted((RULES / "merged").glob("deer_*.yaml"))
    jobs = sys.argv[1:] or [p.stem[len("deer_"):] for p in merged_files]
    all_ids = Counter()
    total_bad = 0
    for job in jobs:
        f = RULES / "merged" / f"deer_{job}.yaml"
        errs = []
        entries = yaml.safe_load(f.read_text(encoding="utf-8")) if f.exists() else None
        if not entries:
            print(f"{job}: no merged file")
            total_bad += 1
            continue
        seen = Counter()
        for e in entries:
            eid = e.get("id", "?")
            all_ids[eid] += 1
            members = e.get("members") or []
            if not members:
                errs.append(f"{eid}: no members")
            for m in members:
                seen[m] += 1
                if m not in source:
                    errs.append(f"{eid}: member {m} does not exist")
            persons = {p for m in members if m in source for p in persons_of(source[m], people)}
            if e.get("agreement") != len(persons):
                errs.append(f"{eid}: agreement {e.get('agreement')} but {len(persons)} distinct people")
            for p in sorted(persons - set(mentors)):
                errs.append(f"{eid}: {p} has no line in mentors.yaml")
            listed = set(e.get("people") or [])
            if listed != persons:
                errs.append(f"{eid}: people list {sorted(listed)} != members' people {sorted(persons)}")
            kinds = {kind_of(source[m], people) for m in members if m in source}
            if "research" in kinds and e.get("evidence") != "research":
                errs.append(f"{eid}: has a research member but evidence != research")
            for key, val in (e.get("conditions") or {}).items():
                if key in ranged:
                    continue
                if key not in vocab:
                    errs.append(f"{eid}: unknown condition {key}")
                    continue
                for v in val if isinstance(val, list) else [val]:
                    if v not in vocab[key]:
                        errs.append(f"{eid}: {key} value {v!r}")
            for fld in ("action", "why"):
                if not isinstance(e.get(fld), str) or not e.get(fld):
                    errs.append(f"{eid}: {fld} missing or not text")
        want = {i for i, r in source.items() if r["job"] == job}
        for i in sorted(want - set(seen)):
            errs.append(f"source rule {i} ({job}) is in no merged entry")
        for i, n in seen.items():
            if n > 1:
                errs.append(f"source rule {i} is in {n} merged entries")
        for e in errs:
            print(f"x {job}: {e}")
        n_multi = sum(1 for e in entries if (e.get("agreement") or 0) > 1)
        print(f"{job}: {len(want)} source rules -> {len(entries)} merged "
              f"({n_multi} with 2+ people), {len(errs)} problems")
        total_bad += len(errs)
    for i, n in all_ids.items():
        if n > 1:
            print(f"x duplicate merged id {i}")
            total_bad += 1
    sys.exit(1 if total_bad else 0)


if __name__ == "__main__":
    main()

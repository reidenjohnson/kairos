"""Check a Kairos rules file against its tag vocabulary before anything loads it.

Usage:
    python tools/validate_rules.py notes/rules [notes/rules/rule_schema.yaml]
    python tools/validate_rules.py notes/pilot/rules.yaml [notes/pilot/rule_schema.yaml]

A folder is checked as one set: every *.yaml under it except rule_schema.yaml, ids unique across
all of them. Timestamps are optional (Reiden 2026-09-26: a video link is enough).

Fails (exit 1) on: missing required fields, unknown fields or tag values, duplicate ids, a source
video that isn't in notes/transcripts/, a timestamp past the end of its video, or a concept /
related / conflict link that points nowhere. A rule that fails here would silently never match
in the engine, so this is the gate.
"""

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
TRANSCRIPTS = ROOT / "notes" / "transcripts"
MARK = re.compile(r"\[(?:(\d+):)?(\d+):(\d\d)\]")


def seconds(t):
    parts = [int(x) for x in str(t).split(":")]
    return sum(x * 60 ** i for i, x in enumerate(reversed(parts)))


def video_lengths():
    """videoId -> last [m:ss] mark in its saved transcript (None if it has no marks yet)."""
    out = {}
    for p in TRANSCRIPTS.rglob("*.txt"):
        m = re.search(r"\[([\w-]{11})\]\.txt$", p.name)
        if m:
            marks = MARK.findall(p.read_text(encoding="utf-8"))
            out[m.group(1)] = seconds(":".join(x for x in marks[-1] if x)) if marks else None
    return out


def concept_ids(rules_path):
    doc = (rules_path if rules_path.is_dir() else rules_path.parent) / "concepts.md"
    if not doc.exists():
        return set()
    return set(re.findall(r"\{#([\w-]+)\}", doc.read_text(encoding="utf-8")))


def check(rules_path, schema_path):
    if rules_path.is_dir():
        rules = []
        for p in sorted(rules_path.rglob("*.yaml")):
            # merged/ is the consensus layer (check_merged.py); people.yaml is the video index.
            if p.name not in ("rule_schema.yaml", "people.yaml") and "merged" not in p.parts:
                rules += yaml.safe_load(p.read_text(encoding="utf-8")) or []
    else:
        rules = yaml.safe_load(rules_path.read_text(encoding="utf-8")) or []
    schema = yaml.safe_load(schema_path.read_text(encoding="utf-8"))
    allowed = set(schema["required"]) | set(schema["optional"])
    vocab = schema["conditions"]
    ranged = set(vocab.pop("ranged", []))
    lengths = video_lengths()
    concepts = concept_ids(rules_path)
    ids = [r.get("id") for r in rules]
    known_ids = set(ids)
    errors = []

    for dup in {i for i in ids if ids.count(i) > 1}:
        errors.append(f"duplicate id {dup}")

    for r in rules:
        rid = r.get("id", "?")
        err = lambda msg: errors.append(f"{rid}: {msg}")
        for f in schema["required"]:
            if f not in r:
                err(f"missing {f}")
        for f in r:
            if f not in allowed:
                err(f"unknown field {f}")
        if r.get("species") not in schema["species"]:
            err(f"species {r.get('species')!r}")
        if r.get("job") not in schema["job"]:
            err(f"job {r.get('job')!r}")

        for key, val in (r.get("conditions") or {}).items():
            if key in ranged:
                if not re.fullmatch(r"(<=|>=|<|>)?\s*-?\d+(\.\d+)?(\s*-\s*-?\d+(\.\d+)?)?", str(val).strip()):
                    err(f"{key} range {val!r}")
            elif key in vocab:
                for v in val if isinstance(val, list) else [val]:
                    if v not in vocab[key]:
                        err(f"{key} value {v!r}")
            else:
                err(f"unknown condition {key}")

        eng = r.get("engine") or {}
        if eng.get("can_test") not in schema["engine_can_test"]:
            err(f"engine.can_test {eng.get('can_test')!r}")
        if eng.get("can_test") != "full" and not eng.get("missing"):
            err("engine.missing is empty but can_test isn't full")
        # An unquoted "a: b" inside [ ] parses as a dict; catch it instead of loading garbage.
        for f in ("action", "why", "avoid", "context"):
            if f in r and not isinstance(r[f], str):
                err(f"{f} is not plain text (quote it)")
        for f in ("have", "missing"):
            for item in eng.get(f) or []:
                if item not in schema["engine_inputs"][f]:
                    err(f"engine.{f} {item!r} is not in rule_schema engine_inputs.{f}")

        for s in r.get("sources") or []:
            vid = s.get("video")
            if vid not in lengths:
                err(f"source video {vid} not in notes/transcripts/")
            elif s.get("t") and lengths[vid] is not None and seconds(s["t"]) > lengths[vid] + 30:
                err(f"timestamp {s.get('t')} is past the end of {vid}")
        if len(r.get("sources") or []) < 1:
            err("no sources")

        for c in r.get("concepts") or []:
            if c not in concepts:
                err(f"concept {c} not found in concepts.md")
        for link in r.get("related") or []:
            if link not in known_ids:
                err(f"related {link} is not a rule id")
        for link in r.get("conflicts") or []:
            if link not in known_ids and link not in concepts:
                err(f"conflict {link} is not a rule or concept id")

    return rules, errors


def main():
    rules_path = Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / "notes" / "pilot" / "rules.yaml")
    schema_path = Path(sys.argv[2]) if len(sys.argv) > 2 else (
        rules_path / "rule_schema.yaml" if rules_path.is_dir() else rules_path.with_name("rule_schema.yaml"))
    rules, errors = check(rules_path, schema_path)
    for e in errors:
        print("x", e)
    print(f"{len(rules)} rules, {len(errors)} problems")
    if not errors:
        # The engine's data to-do list: which missing input would unlock the most rules.
        counts = {}
        for r in rules:
            for m in r["engine"].get("missing") or []:
                counts[m] = counts.get(m, 0) + 1
        print("\nMissing engine inputs, by rules they'd unlock:")
        for m, n in sorted(counts.items(), key=lambda kv: -kv[1]):
            print(f"  {n:3}  {m}")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()

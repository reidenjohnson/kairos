"""Rewrite each merged entry's `people:` and `agreement:` from its members.

Run after changing people.yaml or a rule's `speaker:`, then run check_merged.py. Only those two
lines are touched, so the rest of the hand-written merged files keeps its formatting.

Usage: python tools/sync_merged_people.py
"""
import re
from pathlib import Path

import yaml

from check_merged import RULES, persons_of


def main():
    people = yaml.safe_load((RULES / "people.yaml").read_text(encoding="utf-8"))
    source = {}
    for p in sorted((RULES / "deer").glob("*.yaml")):
        for r in yaml.safe_load(p.read_text(encoding="utf-8")) or []:
            source[r["id"]] = r

    changed = 0
    for f in sorted((RULES / "merged").glob("deer_*.yaml")):
        text = f.read_text(encoding="utf-8")
        entries = {e["id"]: e for e in yaml.safe_load(text) or []}
        # Split into entry blocks at each top-level "- id:" so edits stay inside their entry.
        parts = re.split(r'(?m)^(?=- id: )', text)
        out = []
        for block in parts:
            m = re.match(r'- id: "?([^"\n]+)"?', block)
            e = entries.get(m.group(1)) if m else None
            if e:
                persons = sorted({p for mm in e.get("members", []) if mm in source
                                  for p in persons_of(source[mm], people)})
                new_people = "  people: [" + ", ".join(f'"{p}"' for p in persons) + "]"
                # `people:` is written either as one flow list or as a block list of "  - ..." lines.
                new_block = re.sub(r"(?m)^  people:(?: .*|\n(?:  - .*\n?)+)$", lambda _: new_people, block, count=1)
                new_block = re.sub(r"(?m)^  agreement: .*$", f"  agreement: {len(persons)}", new_block, count=1)
                if new_block != block:
                    changed += 1
                    print(f"{e['id']}: {e.get('agreement')} -> {len(persons)}  {persons}")
                block = new_block
            out.append(block)
        f.write_text("".join(out), encoding="utf-8")
    print(f"{changed} entries updated")


if __name__ == "__main__":
    main()

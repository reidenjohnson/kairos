"""Draft the Learn curriculum: every merged rule entry placed in one unit, levels beginner -> pro.

Writes notes/learn/curriculum.yaml (machine-readable) and notes/learn/CURRICULUM.md (outline).
Units list merged-entry slugs (id minus "m-<job>-" and "-NN"); the script fails if any merged
entry is unplaced or placed twice, so nothing from the videos gets lost.
"""
import glob
import re
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
MERGED = ROOT / "notes" / "rules" / "merged"
OUT = ROOT / "notes" / "learn"

# (level, unit title, one-line goal, [slugs])
UNITS = [
    ("beginner", "Safety first", "Get up and down the tree without ever being unclipped.", """
        stay-connected-ground-to-ground tether-before-unclipping tether-high lifeline practice-at-home-low
        stand-offset-step-down haul-gear-with-pull-rope cam-stand-tight pick-healthy-tree climber-step-in-low
        inspect-straps platform-push compass-big-woods drone-laws"""),
    ("beginner", "Your gear", "What to bring, what to leave, and how to pick a stand or saddle.", """
        go-light cover-hands-face quiet-clothes-no-clang harness-under-jacket hydrate-before no-big-pack-in-tree
        hang-weapon-on-hook pack-low-out-of-shot bow-ready-first dim-light-not-colored red-green-headlamp
        spare-release draw-weight-you-can-hold dress-warm-cold rubber-boots know-your-height
        know-and-practice-your-setup saddle-for-mobile-quiet hang-on-all-day-comfort saddle-all-day-comfort
        hang-on-in-cold"""),
    ("beginner", "Making the shot", "Practice, pick a spot, and only take shots you'll make.", """
        beginner-any-legal-deer keep-bow-shots-close double-lung-only aim-low-forward aim-behind-shoulder
        practice-before-season blind-bale-judge-distance 3d-target-hold-at-draw draw-when-head-hidden
        run-the-script pick-a-spot range-marks kneel-and-seated-practice rifle-rest eyes-closed
        movement-over-noise"""),
    ("beginner", "After the shot", "When to wait, when to track, and how to get your deer out.", """
        marginal-hit-wait gut-shot-alive same-night-with-help level-shot-bearing plan-the-extraction
        float-ungutted"""),
    ("beginner", "Wind and scent basics", "A deer's nose wins; play the wind first, products second.", """
        wind-in-your-face wind-before-products dont-force-wrong-wind check-real-air-milkweed
        morning-thermals-rise evening-thermal-drain cant-fool-the-nose scent-free-at-home clothes-kept-apart
        baking-soda-wash boot-soles pants-in-boots no-scent-trail"""),
    ("beginner", "How deer live", "Bed, food, and the path between them.", """
        find-bed-and-food start-at-food bedding-layers-from-food between-bedding-and-food deer-types
        read-trails daylight-moves-in-cover season-focus"""),
    ("beginner", "When to go", "The rut, temperature, rain, and picking your days.", """
        rut-go-regardless warm-weather-hurts big-temp-drop light-rain-go skip-extreme-weather
        stay-mid-morning pick-best-weather-days forecast-horizon read-the-day sleep fronts-weather-small-edge"""),
    ("beginner", "Getting in and out", "Deer that never know you were there.", """
        exit-unseen light-discipline hidden-route pack-gear-quiet quiet-climb-one-trip muffle-the-ratchet
        setup-time pick-tree-and-route-now partner-shot-protocol"""),

    ("intermediate", "Picking the stand", "Where to sit for mornings, evenings, and the wind you have.", """
        staging-cover-off-field-edge on-the-way-food-not-destination evening-near-bedding-staging
        shade-toward-food-end morning-back-in-cover morning-back-side-of-bedding scent-over-no-deer-ground
        inside-corners-and-transitions trail-and-terrain-junctions small-shooting-lanes trim-lanes-from-stand
        cover-behind-you no-skyline shot-on-strong-side stand-height-low stand-height-high stand-level-or-back
        right-tree-not-easy-tree branchy-tree-for-hang-on walk-the-tree-deer-eyes saddle-trunk-between
        stay-outside-edge-pull-deer-in early-season-leaf-cover stand-work-in-summer stand-work-in-rain-wind"""),
    ("intermediate", "Scouting and sign", "Find deer first, then read what they left.", """
        find-deer-first observation-sit glass-open-country traditional-sign read-rubs rub-direction subtle-sign
        hidden-old-sign hunt-where-deer-are-now bed-at-edges buck-bed-clues marsh-bedding feed-sign-not-acorns
        feed-sign-after-rain scout-mast-yourself walk-more-for-feed-trees next-food-source map-then-verify
        late-winter-scouting post-gun-scout warm-days-scout scout-in-and-out snow-south-slope-beds snow-trails
        mark-doe-groups repeat-the-pattern speed-scout"""),
    ("intermediate", "Trail cameras", "What photos really tell you (and what they don't).", """
        cams-point-to-food camera-high-and-dim camera-at-intersections camera-on-scrape
        no-camera-on-active-scrape one-photo-means-more camera-lottery night-only-not-nocturnal
        day-vs-night-photos missing-buck-not-dead cold-scrape-buck-gone limit-camera-checks cards-in-storm
        leave-cameras track-traps check-cam-history august-lap velvet-shift-find-core velvet-bucks-leave"""),
    ("intermediate", "Food and water", "Hunt the food deer are on right now.", """
        food-in-its-window-now start-at-current-food hunt-dropping-oaks white-oak-hunt-now bust-mast-browse
        hot-dry-juicy-food hot-water-on-the-way waterhole-on-the-route rut-water rare-mountain-water
        waterhole-looks-natural waterhole-lead-time waterhole-never-dry late-cover-next-to-food
        late-bachelor-corn late-season-browse late-afternoon-food"""),
    ("intermediate", "Reading the weather", "Fronts, pressure, wind, rain, heat, and cold: what the data says.", """
        cold-front-trifecta pressure-alone-no rising-barometer falling-baro-midday front-day-after-morning
        front-day-before-morning evening-before-rain ten-degree-drop wind-drop windy-days-still-go
        wind-region-east evening-temp-band cold-dawn-small warm-take-morning cold-mornings-prerut
        early-cold-front-south be-there-when-rain-stops rain-hole-wait-for-drips scrapes-after-storm
        late-season-storm-food late-bitter-cold-early-feed extreme-cold-midday stable-weather-fades
        moon-rise-set moon-phase-ignore feeding-cycle east-wind-slow headwind-moves-earlier lee-in-high-wind
        grass-edge-in-wind late-bed-out-of-wind hot-shady-beds storm-break-remote-food high-wind-drop-below-ridge
        weather-break-food-near-bed rain-washes-your-trail in-before-the-break wind-and-wet-cover-noise"""),
    ("intermediate", "Access and scent, done right", "Protect your spots all season.", """
        rest-before-season dont-push-best-spots-early one-trip-midday-setup cross-food-only-when-empty
        protect-the-food reach-food-from-the-edge park-far-avoid-trails approach-from-below
        climb-from-the-back-side mowed-quiet-trail dead-end-trail two-routes no-scrape-near-entry hang-and-hunt
        ebike silent-entry-near-bed morning-entry-modes bottom-arrive-just-before-light
        prioritize-mornings-pre-rut afternoon-entry-least-bumping morning-known-spots access-scent-plan
        rotate-stands one-bump-not-ruined angle-past-bedded-buck drop-off baking-soda-on-body
        baking-soda-mouth baking-soda-boots-tote field-spray-gear freeze-clothes bland-food ruck-to-sweat-less
        trim-hair rope-up natural-cover-scent"""),
    ("intermediate", "Wind and terrain", "Thermals, bottoms, ridges, and swirling wind.", """
        stands-for-every-wind prevailing-vs-front-winds terrain-over-wind-route old-buck-uses-wind
        morning-curl-at-bench bottom-evening-needs-steady-wind ridge-point-light-over-calm bottom-overtop-swirl
        hunt-high-avoid-valleys creek-crossing-downstream mountain-check-dont-scratch shift-arrives-early
        height-doesnt-beat-wind midday-wind-shift evening-thermal-switch sign-in-swirl-back-out
        morning-sit-above-deer evening-above-empty-bottom"""),
    ("intermediate", "Public land", "Beat the crowd: where others don't go, and where they push deer.", """
        overlooked-spots-close go-deep read-other-hunters public-habitat-edges gun-day-escape-routes
        short-trip-sprint public-full-day-layout enter-where-others-dont leave-no-trail scout-for-people
        unpressured-refuges safest-place-around velvet-public-edges talk-to-hunters"""),
    ("intermediate", "Calling basics", "Call less, call soft, and know when to stop.", """
        call-less start-soft no-blind-calling blind-grunt-interval blind-call-timing grunt-passing-buck
        grunt-circle-back stop-once-he-responds read-first-reaction bleat-low-risk rattle-subtle
        rattle-downwind-blocked early-soft-grunt buck-grunt-tone inflection grunt-breath-control"""),

    ("pro", "Mature bucks", "How old bucks live, bed, and travel differently.", """
        small-daylight-core old-buck-stays-in-core not-always-high thermal-hub-bed not-always-leeward
        hunt-the-route-not-the-bed bed-with-a-view off-the-main-trail terrain-gaps-deer-must-use
        perfect-pinch-not-oldest-buck pinch-two-stands-crosswind rifle-pinch-close saddles-hill-country
        open-crossing-low-dip small-clearcut-edges ridge-crest-parallel-trail hunt-the-thick buck-personality
        big-bucks-loners watch-bedded-buck-from-ridge spooked-buck-hunt-next-door bump-while-scouting
        bumped-buck-hunt-nearby blown-buck-wait creek-bottom-sign-is-night shed-marks-core
        move-to-where-deer-are backup-stands-for-conditions hunt-winds-he-already-uses"""),
    ("pro", "The rut", "Does, doe trails, scrapes, and when to sit all day.", """
        rut-hunt-the-does downwind-of-doe-groups buck-route-across-doe-trails rut-quiet-side rut-all-day
        rut-dead-spot-move rut-sit-the-focal-point late-oct-scrapes satellite-bucks-sign lockdown-barrier
        second-rut late-rut-giants same-dates-every-year save-spot-for-rut-week small-parcel-wait-for-rut
        target-buck-outside-rut rattle-hard-cruiser rattle-peak-to-post-rut snort-wheeze-distant
        snort-wheeze-hung-up ground-noise decoys-open-country-only buck-urine-challenge tarsal-jar
        estrus-in-rut-only doe-urine-all-season rut-bucks-ignore-wind rub-line-marks-route
        perennial-scrapes-pre-rut vanished-buck-wait fawn-dates rut-less-careful"""),
    ("pro", "Mock scrapes and scent tools", "Build them, place them, and skip the bottled stuff.", """
        mock-scrape-on-active-trail mock-scrape-at-the-shot mock-scrape-flat-ground mock-scrape-plot-entrance
        mock-scrape-no-bought-scent mock-scrape-just-install mock-scrape-sightline mock-scrape-early
        mock-scrape-build transplant-scrape scrape-over-rub licking-branch-material licking-branch-waist-high
        paracord-not-cable no-commercial-scent-on-mock-scrape scrape-dripper one-scent-per-scrape skip-lures
        urine-laws"""),
    ("pro", "Hunting one buck", "Strategy: patterns, timing, stand rotation, and access to land.", """
        target-one-buck hunt-log learn-every-hunt wait-for-daylight-pattern dont-chase-fresh-photo
        bait-48-hours stand-burns-with-use big-woods-repeat-sits stand-by-time-of-day morning-or-evening-spot
        morning-mission limit-early-mornings south-early-mornings-oaks velvet-shoot stay-in-one-area
        be-the-sanctuary door-knock-numbers ask-why-on-a-no bait-next-door post-ehd-rebound doe-arrival-test
        early-morning-back-of-bedding"""),
    ("pro", "Advanced gear and shooting", "Saddles, mobile kits, ground setups, and tuning.", """
        uv-brighteners front-weighted-arrow saddle-starter-kit saddle-add-ons-order saddle-platform-height
        stick-spacing-platform-plan one-trip-climb-rig light-quiet-mobile-kit pack-quiet bubble-level
        short-gun-tight-woods dry-clothes-at-tree long-range-practice paper-tune shot-routine saddle-weak-side
        hang-for-left-shot downhill-bend-at-hip trad-height-distortion ground-seat-low-profile
        ground-break-up-outline ground-move-slow no-light-in-blind tucked-elevated-blind"""),
]

# Basics a beginner needs that no video in the set teaches yet. Proven-never-guessed: these stay
# empty until a source video covers them.
GAPS = [
    ("beginner", "Licenses, hunter ed, and the law", "Hunter education, licenses, tags, legal hours, orange."),
    ("beginner", "Deer basics", "Deer senses, the yearly cycle, bucks vs does, reading age."),
    ("beginner", "Field dressing and meat care", "Gutting, cooling, and getting meat home safely."),
    ("beginner", "Blood trailing basics", "Reading hit sign and blood, and how to follow a trail."),
    ("intermediate", "Maps and e-scouting", "Reading topo, aerials, and onX before you walk."),
    ("pro", "Rifle and big-woods hunting", "Still-hunting, tracking, and big-country tactics."),
]


def main():
    entries = {}
    for f in sorted(MERGED.glob("deer_*.yaml")):
        job = f.stem[len("deer_"):]
        for e in yaml.safe_load(f.read_text(encoding="utf-8")):
            s = e["id"][len(f"m-{'wth' if job == 'when_to_hunt' else job}-"):]
            s = re.sub(r"-\d\d$", "", s)
            if s in entries:
                raise SystemExit(f"slug collision: {s}")
            entries[s] = e
    placed = Counter()
    for _, _, _, slugs in UNITS:
        for s in slugs.split():
            placed[s] += 1
    unknown = [s for s in placed if s not in entries]
    missing = [s for s in entries if s not in placed]
    twice = [s for s, n in placed.items() if n > 1]
    if unknown or missing or twice:
        raise SystemExit(f"unknown: {unknown}\nunplaced: {missing}\nplaced twice: {twice}")

    OUT.mkdir(parents=True, exist_ok=True)
    doc, md = [], ["# Kairos Learn: draft curriculum (deer)", "",
                   f"{len(entries)} consensus lessons from 75 videos, in {len(UNITS)} units. "
                   "Stars = number of independent people who teach it; R = measured research.", ""]
    for level in ("beginner", "intermediate", "pro"):
        md += [f"## {level.capitalize()}", ""]
        for lv, title, goal, slugs in UNITS:
            if lv != level:
                continue
            ids = [entries[s]["id"] for s in slugs.split()]
            doc.append({"level": lv, "unit": title, "goal": goal, "entries": ids})
            strong = sorted((entries[s] for s in slugs.split()),
                            key=lambda e: (-e["agreement"], e["evidence"] != "research"))
            md.append(f"**{title}** ({len(ids)}): {goal}")
            for e in strong[:3]:
                tag = f"{'★' * min(e['agreement'], 5)}{' R' if e['evidence'] == 'research' else ''}"
                md.append(f"- {tag} {e['action'][:140].rstrip()}{'...' if len(e['action']) > 140 else ''}")
            md.append("")
        gaps = [g for g in GAPS if g[0] == level]
        if gaps:
            md.append("*Missing (no source video yet):* " + "; ".join(f"{t} ({d})" for _, t, d in gaps))
            md.append("")
    doc.append({"gaps": [{"level": l, "unit": t, "needs": d} for l, t, d in GAPS]})
    (OUT / "curriculum.yaml").write_text(yaml.safe_dump(doc, sort_keys=False, allow_unicode=True, width=110),
                                         encoding="utf-8")
    (OUT / "CURRICULUM.md").write_text("\n".join(md), encoding="utf-8")
    print(f"{len(entries)} entries placed in {len(UNITS)} units; {len(GAPS)} gap units flagged")


if __name__ == "__main__":
    main()

# Kairos — evidence & sources

Every factor the scoring engine uses, what the research actually says, and how confident
we are. Weights are **derived from the strength and direction of this evidence**, not guessed.
Where no study quantifies an exact weight (which is most of the time), we say so and use the
relative evidence strength to set it, then tune against real results over time.

Confidence tiers:
- **Tier 1 — measured:** peer-reviewed / agency studies with numbers.
- **Tier 2 — strong consensus:** widely reported by experts/agencies, direction clear, exact size fuzzy.
- **Tier 3 — approximation:** our modeling choice where data is thin or we lack a live input (flagged).

---

## Cross-cutting factors

### Temperature (Tier 1 for most game; the single strongest driver)
- Deer: Mississippi State found **temperature influenced deer movement more than any other weather variable**. [MeatEater](https://www.themeateater.com/wired-to-hunt/whitetail-hunting/does-barometric-pressure-affect-deer-movement)
- Moose: cold-adapted; heat-stress thresholds commonly cited at **14°C and 20°C (57–68°F)**, with reduced travel between **14–24°C**. Activity shifts hard to dawn/dusk on warm days. [J. Mammalogy](https://academic.oup.com/jmammal/article/100/1/169/5299335), [Renecker & Hudson via Alces](https://www.alcesjournal.org/index.php/alces/article/download/1941/2051/4683)
- Elk: **heat suppresses daytime activity** (bed in shade), **cold spurs all-day movement**. [Outdoor Canada](https://www.outdoorcanada.ca/elkweather/)
- Bear (fall): during hyperphagia bears feed up to **20 hrs/day**; **cold spurs feeding, heat reduces activity**; Sept–Oct peak. [HuntWise](https://huntwise.com/field-guide/bear/best-times-to-hunt-black-bear), [DiveBomb](https://www.divebombindustries.com/blogs/news/how-black-bears-move-before-and-after-storms)

### Barometric pressure — trend (Tier 1 for fish, Tier 2 for game)
- Bass: on a **slowly falling** barometer **65% of bass struck** lures; on a slowly rising one only **30%**. Falling pressure before a front = feeding frenzy. [In-Fisherman](https://www.in-fisherman.com/editorial/barometric-pressure-and-bass/153689), [Mossy Oak](https://www.mossyoak.com/our-obsession/blogs/the-fishing-and-barometric-pressure-relationship)
- Deer: rapid drops of **0.4–0.5 inHg** associated with greatest activity. [Deer & Deer Hunting](https://www.deeranddeerhunting.com/content/articles/1-how-weather-affects-deer-behavior-alsheimers-greatest-insights)

### Barometric pressure — absolute range (Tier 1, deer)
- Illinois biologist Keith Thomas: greatest whitetail feeding at **29.80–30.29 inHg**; best movement toward the high end (~30.1–30.3). [MidWest Outdoors](https://midwestoutdoors.com/hunting/hunting-december-issue-barometric-pressure-and-whitetail-movement/)
- Fish comfort band widely cited **29.70–30.40 inHg** (stable = consistent). [Tempest](https://tempest.earth/resources/barometric-pressure-and-fishing/)

### Cold fronts (Tier 2, strong)
- Waterfowl: cold fronts + wind push new, unpressured migrating birds; **the day before the front passes** is prime. [Ducks Unlimited](https://www.ducks.org/hunting/waterfowl-hunting-tips/forecast-your-duck-hunting-success-weather-matters)
- Deer/bear move ahead of storms. [ScentLok](https://www.scentlok.com/utilizing-barometric-pressure-and-cold-fronts-for-whitetail-buck-harvesting/)

### Wind (Tier 2, quantified for ducks)
- Ducks: sweet spot **10–15 mph**; **under 6–7 mph** decoys look dead; **over 20–25 mph** birds seek shelter. [Realtree](https://realtree.com/the-duck-blog/how-much-wind-do-duck-hunters-need)
- Upland: **calm, dry** days hold scent for dogs; high wind hurts. [Minnesota DNR](https://www.dnr.state.mn.us/gohunting/ruffed-grouse-and-woodcock-hunting.html)
- Walleye: **"walleye chop"** — moderate wind breaks light penetration and turns fish on. [Northern Ontario](https://northernontario.travel/fishing/wind-cloud-and-walleye-why-its-important-understand-weather-when-fishing)

### Cloud cover (Tier 2)
- Walleye own low light (tapetum lucidum); **overcast + chop** extends feeding all day. [Mack's Lure](https://mackslure.com/blogs/mack-attack/harrington-how-light-intensity-impacts-walleye-fishing)
- Overcast encourages daytime game movement.

### Moon / solunar (Tier 1 — mostly debunked, kept near-zero on purpose)
- Deer: 22,000+ GPS points → solunar tables **~25% accurate**; deer are crepuscular regardless. [MeatEater](https://www.themeateater.com/wired-to-hunt/whitetail-hunting/new-research-confirms-the-moon-doesnt-affect-deer-movement)
- Fish: a **2023 North American Journal of Fisheries Management** study found solunar tables **failed to predict** trout fishing success. [FishingBooker summary](https://fishingbooker.com/blog/solunar-fishing-calendars-fishing-by-moon-phases/)
- **Where the moon DOES earn weight:**
  - Snowshoe hare: **full moon → 2.5× more predation** in snowy season; hares reduce movement (bright moon = worse for daytime hunting demand). [Griffin et al.](https://www.umt.edu/mills-lab/files/2015/01/griffin05moonlight.pdf)
  - Walleye: big fish concentrate **~3 days around new & full moon**. [Northern Ontario](https://northernontario.travel/fishing/wind-cloud-and-walleye-why-its-important-understand-weather-when-fishing)

### Folklore / traditional wisdom — a LABELED secondary voice (2026-09-06), never scored
Reiden's call: don't gut the old-timer knowledge, but never fake it and never let it drive the
number. The **"Old-timer's read"** ([app/advice/Folklore.kt]) surfaces one relevant traditional
saying for today's weather, tagged **BACKED** (the science agrees — usually because the old-timers
were reading the barometer without a gauge; Kairos scores the same signal) or **TRADITION** (weak or
no evidence; shown as color, marked unproven, kept out of the score). Nothing here moves the score.
- **BACKED** — "feed hard just before the weather breaks" = the falling barometer / pre-front feed,
  the most reliable cue and already scored (see *Barometric pressure* above); fall cold-snap feed-up
  and dog-days-fish-deep are documented seasonal patterns (see the *water temperature* + coldwater
  sections); bright-calm tough bite = light penetration.
- **TRADITION** — the wind rhymes ("wind from the east, fish bite the least / west, bite's best") are
  folklore; what correlation exists is the *front* the wind rides with, not the compass. Presented as
  such. [In-Fisherman on wind](https://www.in-fisherman.com/editorial/wind-and-fishing/151806)
- The moon saying is included on purpose to state, out loud, the one tradition Kairos refuses to score
  (ties back to the *Moon / solunar* section above).

### Time of day (Tier 1) — used for the "best window," not the daily score
- Deer/elk crepuscular: peaks at **sunrise and end of civil twilight**. [PLOS ONE](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0106997)
- Grouse/woodcock: **3 hrs after sunrise, 3 hrs before sunset**; midday they loaf. [Minnesota DNR](https://www.dnr.state.mn.us/gohunting/ruffed-grouse-and-woodcock-hunting.html)

---

## Fish water temperature (Tier 1 preferences; Tier 3 our proxy)
Preferred ranges:
- Largemouth bass optimum **80–84°F** (feed 41–98°F). [In-Fisherman](https://www.in-fisherman.com/editorial/largemouth-bass-temperature-thermoclines/494247)
- Smallmouth bass **65–78°F**. [Bassmaster](https://www.bassmaster.com/how-to/news/smallmouth-and-temperature/)
- Landlocked salmon **< 65°F**; lake trout (togue) **55–60°F**; brook trout **50–65°F**, stress at 68°F. [Maine IFW](https://www.maine.gov/ifw/fishing-boating/fishing/maine-fishing-guide/catch-specific-fish.html)

**Water temperature is now TIERED (2026-09-06), real where possible, labeled where estimated.**
The free weather feed gives **air** temperature, not lake water temperature, which is what fish
respond to — historically the #1 fishing-accuracy gap. The resolver ([engine/WaterTemp.kt],
[engine/data/UsgsWater.kt]) now prefers measured data and labels anything it estimates, most-trusted
first:
- **Your reading** — a thermometer reading the user enters in Settings; best for the small pond you're
  standing on. Trusted for 3 days, then it hands back to a gauge or the estimate.
- **USGS gauge** — a real continuous sensor near the water, via the **USGS Water Services
  instantaneous-values API** (waterservices.usgs.gov, free, no key), parameter code **00010 = water
  temperature, °C**. We keep the nearest gauge whose latest reading is within 12 h and 30 mi, convert
  °C→°F, and disclose its age + distance. USGS NWIS: https://waterservices.usgs.gov/docs/instantaneous-values/
  Parameter 00010: https://help.waterdata.usgs.gov/parameter_cd?group_cd=PHY
- **Satellite lake-surface temp** — measured, broad, coarse. **Not yet wired** (no free point-query
  API found); the tier exists so the honesty holds when it is added.
- **Estimate (labeled)** — when nothing covers the water: the month's seasonal water normal
  ([SEBAGO_WATER_F], deep coldwater lake) nudged by how far the **recent multi-day average air temp**
  sits from that month's **normal air** ([MAINE_AIR_NORMAL_F], approx. NOAA 1991–2020 normals for
  Portland, ME / KPWM). The nudge is damped (0.35× the anomaly) and clamped (±6°F), because a lake's
  surface tracks a *sustained* air anomaly slowly and partially — a hot afternoon can't claim the lake
  jumped several degrees. This is a disclosed heuristic, not a fitted coefficient; the surface-temp /
  multi-day-air-temp relationship is well established (e.g. lake-temperature modeling literature).
  Still calibrated to a Maine lake and applied anywhere, so it's clearly marked an estimate in-app.

Every tier is surfaced honestly on the Weather screen (source label + age/distance or "estimated").

---

## Weight table (how much each factor drives each species' daily score, 0–1)

| Species | temp | p.trend | p.range | front | wind | cloud | moon |
|---|---|---|---|---|---|---|---|
| Whitetail deer | .32 | .10 | .15 | .26 | .12 | .05 | — |
| Moose | .47 | .10 | .05 | .20 | .12 | .06 | — |
| Elk | .40 | .10 | .10 | .21 | .12 | .07 | — |
| Black bear | .35 | .15 | .05 | .28 | .07 | .10 | — |
| Snowshoe hare | .30 | .10 | .05 | .10 | .20 | .05 | .20 (inverse) |
| Upland birds | .35 | .10 | .05 | .10 | .25 | .15 | — |
| Waterfowl | .13 | .18 | .02 | .32 | .30 | .05 | — |
| Wild turkey | .20 | .12 | .10 | .13 | .30 | .15 | — |
| Coyote | .28 | .12 | .12 | .18 | .18 | .12 | — |
| Largemouth bass | .23 | .40 | .20 | .08 | .05 | .04 | — |
| Smallmouth bass | .23 | .40 | .20 | .08 | .05 | .04 | — |
| Brook trout | .45 | .25 | .10 | .10 | .05 | .05 | — |
| Landlocked salmon | .40 | .28 | .10 | .10 | .06 | .06 | — |
| Lake trout (togue) | .48 | .22 | .10 | .08 | .06 | .06 | — |
| Northern pike | .22 | .34 | .16 | .12 | .10 | .06 | — |
| Chain pickerel | .20 | .34 | .18 | .10 | .10 | .08 | — |
| Yellow perch | .25 | .28 | .20 | .07 | .08 | .12 | — |
| White perch | .24 | .28 | .18 | .08 | .10 | .12 | — |
| Black crappie | .24 | .30 | .16 | .10 | .08 | .12 | — |
| Panfish (sunfish) | .30 | .26 | .18 | .06 | .08 | .12 | — |
| Walleye | .18 | .18 | .07 | .08 | .22 | .22 | .05 (new/full) |

Rationale by group (all weights sum to 1.0):

- **Big game (deer, moose, elk, bear)** — temperature-and-front driven; moose is the most
  heat-sensitive, bear the most front-triggered as it feeds up before denning.
- **Snowshoe hare** — wind + an inverse moon term (bright nights suppress movement).
- **Upland birds** — wind-and-cloud sensitive; calm, mild days scent and hold best.
- **Waterfowl** — front-and-wind; new weather pushes migrants and wind keeps them working.
- **Wild turkey** — wind is the dominant negative (turkeys hunt by eye and ear; moving cover
  and roar both spook them and mute calling), with heavy overcast/rain a secondary suppressor;
  temperature only matters at the extremes, and there is no cold-front trigger the way deer have.
- **Coyote** — a cold-loving predator: cold and post-front conditions move it, and light wind
  keeps a call audible; scored year-round since Maine has no closed daytime season.
- **Bass (largemouth, smallmouth)** — pressure-trend driven with a water-temp suitability gate.
- **Coldwater fish (brook trout, landlocked salmon, lake trout/togue)** — heavily temperature-
  gated (a hard cliff in warm water); togue holds the coldest water so it weights temp highest,
  brook trout next, salmon a touch less (it will chase smelt up into a trolling chop).
- **Pike & pickerel** — cool-water ambush predators, strongly pressure-trend/front driven; they
  feed hard as a front approaches. Pickerel's temperature window is wider (it bites through the ice).
- **Perch, crappie, panfish** — pressure-and-cloud sensitive schooling fish; crappie is the most
  front-shy (a bluebird post-front sky shuts it off), panfish the most simply warm-water driven.
- **Walleye** — light-and-wind, with the only other non-zero moon term (new/full windows).
- **Moon** stays near-zero everywhere except hare and walleye, per the evidence above.

New-species tuning note: the eleven species added/split in this pass (turkey, coyote; the three
coldwater fish; pike, pickerel, the two perch, crappie, panfish) are tuned from the same factor
model and the established behavioral consensus below (and the general-behavior sources cited in
the seasons/Game-Plan sections), not from a study that quantifies an exact weight — so the numbers
are honest, defensible starting points in the shape of that consensus, not precise measurements.

---

## Game Plan advice — behavioral sources (the tactical "why")

The Game Plan (the app's plain-language "here's what to do today") reasons from the same
conditions the score uses, but adds *behavioral* guidance: where the fish/game are by
season, and how today's weather changes what to do. It is written from the consensus of
established angling/hunting knowledge, cross-referenced below. It is labeled guidance,
never a guarantee, and it does not invent certainty.

**Wind (fish are generally *more* active in wind, up to fishable limits):** surface chop
reduces light penetration so bass feed bolder and less warily; wave action adds dissolved
oxygen; and wind concentrates plankton on the windward bank, which draws baitfish and the
bass that hunt them (the "wind-blown bank" pattern). Baitfish follow the food, not the
current itself.
- BassResource, "Fishing When the Wind Blows" — https://www.bassresource.com/fishing/wind_fish.html
- Bassmaster, "Windy day wisdom" — https://www.bassmaster.com/how-to/news/windy-day-wisdom/

**Barometric pressure / falling ahead of a front:** bass commonly go on an aggressive
pre-front feed as pressure falls; it's the *rate of change* (and the weather the change
signals) that matters, not the absolute number. The direct mechanism (swim-bladder comfort)
is debated and one controlled study found no significant direct effect, so we treat pressure
as a weather proxy, honestly.
- Mercury Marine, "How Barometric Pressure Affects Fishing" — https://www.mercurymarine.com/us/en/lifestyle/dockline/how-barometric-pressure-affects-fishing
- VanderWeyst (2014), yellow-perch feeding vs. barometric pressure (no significant direct effect) — https://www.bemidjistate.edu/directory/wp-content/uploads/sites/16/2023/02/2014-VanderWeyst-D.-The-effect-of-barometric-pressure-on-feeding-activity-of-yellow-perch..pdf

**Post-front "bluebird" day (tough bite):** flat wind + bright sky + a sharp temp drop pins
bass tight to cover and deeper; they are light-sensitive and not adapted to the sudden bright
light, so they pull off the bank and barely feed for a day or two → downsize and slow down.
- Louisiana Sportsman, "Bluebird blues — bass fishing after a front" — https://www.louisianasportsman.com/fishing/bass-fishing/bluebird-blues-expert-tips-for-bass-fishing-after-a-front/

**Rain:** light/moderate rain and overcast reduce light penetration and the surface dimple
masks the fish, so bass move shallow and feed more aggressively; runoff washes food and
oxygen into the shallows, gathering baitfish. A downpour muddies the water → slow down, bold
profiles, target inflows/current.
- On The Water, "Largemouths in the Rain" — https://onthewater.com/fishing-in-the-rain-bass-strategies

**Whitetail movement (weather):** barometric pressure was the strongest single stimulus in
the multi-year "What Makes Whitetails Move" tracking; best daylight movement clusters around
30.10–30.30 inHg and on the rapid drop and the rising barometer *behind* a cold front (the
first cold, clear morning). Warm spells push movement into the night (deer overheat in their
winter coat); high wind makes deer bed in sheltered cover and cuts movement until it calms.
- Mossy Oak, "Barometric Pressure's Influence on Whitetail Movement" — https://www.mossyoak.com/our-obsession/blogs/deer/barometric-pressures-influence-on-whitetail-movement-4
- MeatEater / Wired to Hunt, "Does Barometric Pressure Affect Deer Movement?" — https://www.themeateater.com/wired-to-hunt/whitetail-hunting/does-barometric-pressure-affect-deer-movement

**Whitetail rut timing (photoperiod, NOT weather):** the rut is triggered by day length, so
peak breeding lands the same weeks each year (Maine ~mid-November) regardless of the weather;
weather only changes whether rutting deer move in daylight. (Consistent with our decision to
keep the rut date-driven and never weather-driven for its *timing*.)

**Moon / solunar (kept near-zero, honestly):** evidence for a direct lunar effect on feeding
is genuinely mixed — some reviews find a signal, peer-reviewed CPUE work finds none (air
temperature was a better predictor). We keep moon at near-zero weight except where evidence
supports it (snowshoe hare, walleye new/full windows).
- Springer, "No significant relationship between CPUE and solunar values" — https://link.springer.com/article/10.1007/s42452-023-05379-8

**Wild turkey (wind-dominant):** turkeys hunt by sight and sound, so wind is the biggest
suppressor — over ~10 mph they lean on their eyes, spook more easily, and gobblers stop
strutting; over ~20-25 mph movement and calling both fall off. They favor calm, mild mornings
and drop into sheltered hollows and field edges when it blows. This is why turkey weights wind
highest, with overcast/rain secondary and no cold-front trigger like deer.
- NWTF, "Roll With Weather Changes" — https://www.nwtf.org/content-hub/roll-with-weather-changes

**Coyote (cold- and front-driven):** winter is the prime window — scarce food + breeding push
coyotes to move and hunt through the day, and they respond best to cool temps (~20-50°F), light
wind (calls carry, scent stays put), and stable-to-falling pressure; extreme heat and bitter
cold both cut movement, and calling picks up right after a cold front passes. Scored year-round
since Maine has no closed daytime season.
- Mossy Oak, "Winter is the Best Time to Hunt Coyotes" — https://www.mossyoak.com/our-obsession/blogs/predator/winter-is-the-best-time-to-hunt-coyotes

**Perch / crappie / panfish & pike / pickerel (pressure- and light-driven schooling/ambush
fish):** these warm- and cool-water species are tuned from the same pressure-trend + water-temp
model as bass; the yellow-perch feeding study above is the one controlled data point (it found no
*direct* pressure effect, so trend is treated as a weather proxy for them too). Pike and pickerel
are front-driven ambush feeders; crappie is the most shut down by a bright bluebird post-front sky.

---

## Game Plan advice — per-species tactical patterns (the seasonal where/how)

The season-phase location and lure guidance in each species' Game Plan is written from the
established angling consensus (the sources in §C of the handoff plus reputable written sources),
cross-referenced below. It is labeled guidance, not a guarantee.

**Smallmouth bass (rock + crayfish; a wind fish):** smallmouth relate to hard bottom (rock,
gravel, reefs) rather than weeds, eat crayfish and baitfish, run a little cooler than largemouth,
and feed boldest in wind. Pre-spawn (water upper-40s to upper-50s) is the year's best big-fish
window as they stage on rock outside the flats; summer fish lock onto main-lake rock, humps, and
reefs; fall fish switch from crayfish to chasing baitfish shallower. Crankbaits (crawfish/fire-tiger),
jerkbaits, spinnerbaits when active; tubes, drop-shots, Ned rigs, hair jigs, blade baits when tough.
- BassResource, "Seasonal Habits of the Smallmouth Bass" — https://www.bassresource.com/fish_biology/smallmouth-seasonal-habits.html

**Walleye (low-light specialist; the "walleye chop"):** a light-gathering eye (tapetum lucidum)
that outperforms prey in dim water, so walleye feed hardest at dawn/dusk/dark and under wind, cloud,
and stain, and slide deep on bright calm days. Spring/post-spawn fish work shallow rock, points, and
weed edges in 6-14 ft (light jig + soft-plastic minnow; lipless in stain); summer fish hold deeper
structure and weed edges (jigs, live-bait rigs, trolled harnesses/cranks); fall fish follow baitfish
shallow and bite blade baits on drops and stickbaits after dark.
- Wired2Fish, "How to Catch Walleye" — https://www.wired2fish.com/walleye/how-to-catch-walleye
- Lurenet, "Spring Walleye Fishing Tactics" — https://www.lurenet.com/blog/spring-walleye-fishing-tactics-locating-jigging-rigging-early-season-eyes/

**Coldwater fish — water temperature runs the year.** All three want cold, oxygen-rich water and
scatter/feed shallow when the whole column is cold (spring ice-out and fall), then retreat to cold
depths through summer.
- **Brook trout:** prefer ~50-65°F and are stressed past the mid-60s (the most heat-sensitive
  trout); shallow/surface and near inlets in spring and fall, pushed to spring holes and depth in
  summer; spawn Oct-Nov over gravel/seeps. Small spinners, spoons, streamers, worms; trolled spoon.
  - Maine IF&W, "How to Catch a Specific Fish" — https://www.maine.gov/ifw/fishing-boating/fishing/maine-fishing-guide/catch-specific-fish.html
  - eatmorebrooktrout, "Water Temperature Effects on Brook Trout Behavior" — https://eatmorebrooktrout.com/water-temperature-seasonal-effects-on-brook-trout-behavior/
- **Landlocked salmon:** smelt-chasers; surface and near shore at ice-out (best window) and again in
  fall staging off tributary mouths, down to the thermocline (~30-50 ft) in summer. Smelt-imitating
  streamers (Grey Ghost), thin spoons, stickbaits up high; lead core / downriggers deep.
  - Maine IF&W, "Landlocked Salmon species information" — https://www.maine.gov/ifw/fish-wildlife/fisheries/species-information/landlocked-salmon.html
- **Lake trout (togue):** ideal near 50°F on hard bottom; shallow briefly at ice-out, deep (below
  ~45 ft, often 60-120) on rock structure in summer, onto rocky reefs/shoals (20-60 ft) to spawn in
  fall. Flat-line trolling up high in spring; vertical jigging spoons/tubes on deep rock in summer.
  - Mercury Marine, "Lake Trout Fishing Tactics for the Open-Water Season" — https://www.mercurymarine.com/us/en/lifestyle/dockline/lake-trout-fishing-by-the-season

**Pike & pickerel (weed-ambush predators):** hold in and along vegetation and cover and intercept
prey; feed hardest on falling pressure and in the fall feed-up (the trophy window). Pike run cooler
and bigger and slide to deeper cover in summer heat; pickerel are smaller and more heat/cold
tolerant, staying in the shallow weeds and biting through the ice. Spoons, spinnerbaits, jerkbaits,
weedless soft plastics; wire/heavy leader for the teeth; bigger baits for fall pike.
- Wired2Fish, "Pike Fishing: Complete Guide for Every Season" — https://www.wired2fish.com/musky-pike/pike-fishing-guide

**Panfish (yellow perch, white perch, black crappie, sunfish/bluegill):** schooling fish; shallow to
spawn in spring, slightly deeper/cooler in summer, tight schools in fall. Light splits them: crappie
and white perch are low-light suspending feeders (dawn/dusk/dark, off the bottom on brush/structure),
yellow perch and bluegill feed by day nearer the bottom and cover. Small jigs, minnows/worms, tiny
spoons; slow fall in cold water; crappie is the most bluebird-shy of the group.
- DSG Outerwear, "The Ultimate Panfish Guide: Catching Crappie and Bluegill Year-Round" — https://www.dsgouterwear.com/blogs/news/the-ultimate-panfish-guide-catching-crappie-and-bluegill-year-round
- Black Lake NY, "Locating Black Lake Fish" (perch/crappie/bluegill seasonal location) — https://blacklakeny.com/locating-black-lake-fish/

### Hunt species

**Moose & elk (heat-sensitive rut cervids):** the rut (late Sep-early Oct moose; Sep elk) is set by
day length and makes calling work; both overheat badly, so cool/damp/post-front weather moves them
and warm weather pins them to shade/water and the night. (Elk are not a Maine game species; the plan
is honest general western-elk guidance for the score.)
- Northwoods Sporting Journal, "Moose Hunting: Different Seasons, Different Tactics" — https://www.sportingjournal.com/moose-hunting-different-seasons-different-tactics/
- Jason Tomeo Outdoors, "How To Hunt Maine Moose During The Rut" — https://jasontomeoutdoors.com/how-to-hunt-maine-moose-rut/
- Outdoor Canada, "Elk & weather" — https://www.outdoorcanada.ca/elkweather/

**Black bear (food-driven, crepuscular, scent-wary):** fall is a feeding push to fatten before
denning; bait and berries early, hard mast (acorns/beechnuts) later. Most active early morning/late
evening (evening best on bait), pinned to shade/dark by heat, and ruled by wind because they hunt by
nose. Cool, damp weather moves them earlier.
- Bangor Daily News, "How to set up a successful bear bait site in Maine" — https://www.bangordailynews.com/2026/07/24/outdoors/how-to-set-up-bear-bait-site-maine-joam40zk0w/
- Dive Bomb Industries, "Black Bear Hunting in Maine" — https://www.divebombindustries.com/blogs/news/black-bear-hunting-in-maine-best-state-for-bear-hunting

**Snowshoe hare (thick cover, dogs/snow):** live in dense young softwoods, cedar swamps, and regrowth;
hold tight and flush close; run big circles ahead of beagles (post the runs); 3-5 in. of fresh snow
makes tracking. A bright moon lets them feed at night and sit tighter by day (the app's inverse-moon term).
- Bangor Daily News, "With patience and the right strategy, you can track hares in the snow" — https://www.bangordailynews.com/2026/01/18/outdoors/hunting/tracking-hares-winter-woods-joam40zk0w/
- Dive Bomb Industries, "Rabbit Hunting in Maine: Northwoods Snowshoe Strategies" — https://www.divebombindustries.com/blogs/news/rabbit-hunting-in-maine-northwoods-snowshoe-strategies

**Upland birds (grouse & woodcock — edge cover, scenting):** edge specialists of young/thick cover
(alder, aspen, orchards); cool, damp, lightly-breezy weather holds scent for dogs and settles birds,
while hot/dry/windy scatters scent and makes them flush wild; October cold fronts drop fresh woodcock
flights. Pause often, because grouse flush when you stop.
- Project Upland, "Hunting Ruffed Grouse in the Rain" — https://projectupland.com/grouse-species/ruffed-grouse-hunting/how-to-hunt-northwoods-ruffed-grouse-in-the-rain/
- Minnesota DNR, "Hunting ruffed grouse and woodcock" — https://www.dnr.state.mn.us/gohunting/ruffed-grouse-and-woodcock-hunting.html

**Waterfowl (ducks — fronts & wind):** most migration rides the north winds within ~48 h of a cold
front (fresh, decoying birds); wind keeps birds moving, animates decoys, and pushes them into
sheltered lees; freeze-up concentrates them on the last open water; a bright, calm, high-pressure day
is the toughest. Ducks land into the wind, so set the spread accordingly.
- Ducks Unlimited, "Forecast Your Duck Hunting Success: Weather Matters" — https://www.ducks.org/hunting/waterfowl-hunting-tips/forecast-your-duck-hunting-success-weather-matters
- Tidewe, "Fronts and Flights: Using Cold Fronts to Time Duck Migrations" — https://tidewe.org/?p=3620

**Wild turkey (eyes and ears; wind is the spoiler):** roost at night, fly down at first light (prime
hour); wind hides calls, spooks birds, and stops gobbling, so calm days are far better. Spring is a
calling game for gobblers near the roost/hens; fall is find-scatter-and-call-back a flock on the food.
- NWTF, "Roll With Weather Changes" — https://www.nwtf.org/content-hub/roll-with-weather-changes
- Mossy Oak, "Hunting Fall Turkeys: Breaking Up Isn't Always Hard to Do" — https://www.mossyoak.com/our-obsession/blogs/turkey/hunting-fall-turkeys-breaking-up-isnt-always-hard-to-do

**Coyote (a caller's game):** come to prey-distress and (in the late-winter breeding season) coyote
vocals; move/hunt more in cold and low light (dawn/dusk/dark), best on cold, post-front, light-wind
days when calls carry and scent is controlled; a stiff wind or warm spell hurts. They circle downwind
to scent-check, so the setup lives on the wind.
- Mossy Oak, "Winter is the Best Time to Hunt Coyotes" — https://www.mossyoak.com/our-obsession/blogs/predator/winter-is-the-best-time-to-hunt-coyotes
- Outdoor Life, "Best Weather For Coyotes" — https://www.outdoorlife.com/blogs/hunting-andrew-mckean/2011/01/best-weather-coyotes/

---

## Facts reference — rut phases & spawn windows (the in-app "facts you'd Google")

The per-species Facts card is **date-based, not weather-based**: the rut runs on day length and the
spawn runs on water temperature, so both land the same stretch every year (the timing, not whether an
animal moves in daylight that day). Windows are the established regional consensus, cross-referenced
with the behavioral and tactical sources above and the official Maine IF&W species pages each card
links to. Highlights: whitetail peak breeding ~mid-November (photoperiod-driven, calendar-consistent);
moose rut late Sep-early Oct; bass spawn ~60-65°F (May-June); walleye/pike spawn just after ice-out
(40-50°F); coldwater trout/salmon/togue spawn Oct-November; crappie ~60-65°F and bluegill ~68-75°F in
late spring/early summer. These are reference facts labeled as guidance, never a live prediction.
- Maine IF&W species information pages (deer, moose, bear, turkey, migratory birds, fisheries "catch a
  specific fish") — linked per species from the app's Facts card.

---

## License & lottery deadlines (the in-app "apply before it closes" reminders)

Maine's two draw-by-lottery permits require applying months ahead. The app stores the last officially
posted cycle's dates (2026) and, once a window passes, honestly says the next cycle is not yet posted
rather than guessing shifting dates. The "typically" cadence notes are approximate historical patterns,
labeled as guidance in-app, never presented as official dates.

**Moose permit lottery (2026):** applications open April 1; application deadline May 18, 2026 (11:59 pm
ET); drawing Saturday, June 20, 2026 (Acton Fairgrounds).
- Maine IF&W, "Moose Permit" — https://www.maine.gov/ifw/hunting-trapping/hunting/species/moose/moose-permit.html

**Antlerless deer permit lottery (2026):** applications open June 25; application deadline Monday,
August 3, 2026 (11:59 pm); drawing August 13, 2026; permit payment deadline September 10, 2026.
- Maine IF&W, "Antlerless Deer Permit" — https://www.maine.gov/ifw/hunting-trapping/hunting/species/deer/antlerless-deer-permit.html

## Map overlays (the interactive Maine map)

The map shows official State of Maine GIS boundaries exactly as the state publishes them — Kairos does
not draw, estimate, or interpolate any boundary. All layers come from Maine's own ArcGIS org
(`services1.arcgis.com/RbMX0mRVOFNTdLzd`) as GeoJSON, and are cached on-device for offline use. Base map
tiles are free/keyless: USGS The National Map (public-domain topo + imagery) and OpenFreeMap (street).

**Honesty (Tier 3 — approximation, flagged in-app):** the state labels these boundaries **approximate**,
mapped at 1:24,000 (conserved lands / WMAs) or 1:3,000 (expanded archery), and **not legal survey lines**.
For expanded archery, where the map and the **written boundary description** differ, the written
description is the legal authority — so the app shows that description and disclaimer on tap. Public-land
ownership does not imply a right of access; respect posted land and private inholdings.

- Public / conserved land — Maine Office of GIS, "Maine Conserved Lands" —
  https://services1.arcgis.com/RbMX0mRVOFNTdLzd/arcgis/rest/services/Maine_Conserved_Lands_All/FeatureServer
- Expanded archery zones — Maine DIFW, "Expanded Archery Areas" (1:3,000; written description governs) —
  https://services1.arcgis.com/RbMX0mRVOFNTdLzd/arcgis/rest/services/MaineDIFW_ExpandedArcheryAreas/FeatureServer
  — legal descriptions: https://www.maine.gov/ifw/hunting-trapping/hunting/species/deer/expanded-archery/index.html
- Wildlife Management Areas — Maine DIFW, "Wildlife Management Areas" —
  https://services1.arcgis.com/RbMX0mRVOFNTdLzd/arcgis/rest/services/MaineDIFW_WildlifeManagementAreas/FeatureServer
- Wildlife Management Districts (1–29) — Maine DIFW, "Wildlife Management Districts" —
  https://services1.arcgis.com/RbMX0mRVOFNTdLzd/arcgis/rest/services/WMD/FeatureServer
- Base tiles — USGS The National Map (USGSTopo, USGSImageryOnly), public domain —
  https://basemap.nationalmap.gov/ ; OpenFreeMap street style — https://openfreemap.org/
- Rendering — MapLibre Native Android (open-source, no API key) — https://maplibre.org/

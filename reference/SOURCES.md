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

### Time of day (Tier 1) — used for the "best window," not the daily score
- Deer/elk crepuscular: peaks at **sunrise and end of civil twilight**. [PLOS ONE](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0106997)
- Grouse/woodcock: **3 hrs after sunrise, 3 hrs before sunset**; midday they loaf. [Minnesota DNR](https://www.dnr.state.mn.us/gohunting/ruffed-grouse-and-woodcock-hunting.html)

---

## Fish water temperature (Tier 1 preferences; Tier 3 our proxy)
Preferred ranges:
- Largemouth bass optimum **80–84°F** (feed 41–98°F). [In-Fisherman](https://www.in-fisherman.com/editorial/largemouth-bass-temperature-thermoclines/494247)
- Smallmouth bass **65–78°F**. [Bassmaster](https://www.bassmaster.com/how-to/news/smallmouth-and-temperature/)
- Landlocked salmon **< 65°F**; lake trout (togue) **55–60°F**; brook trout **50–65°F**, stress at 68°F. [Maine IFW](https://www.maine.gov/ifw/fishing-boating/fishing/maine-fishing-guide/catch-specific-fish.html)

**Known limitation (Tier 3):** the free weather feed gives **air** temperature, not lake water
temperature, which is what fish actually respond to. For now we approximate Sebago surface
temp with a monthly curve (deep coldwater lake) instead of instantaneous air temp. Future fix:
a real lake-temp source or a user-entered reading. This is flagged in code.

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

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
| Waterfowl | .13 | .18 | .02 | .32 | .30 | .05 | — |
| Largemouth bass | .23 | .40 | .20 | .08 | .05 | .04 | — |
| Smallmouth bass | .23 | .40 | .20 | .08 | .05 | .04 | — |
| Coldwater (salmon/togue/brookie) | .42 | .28 | .10 | .10 | .05 | .05 | — |
| Walleye | .18 | .18 | .07 | .08 | .22 | .22 | .05 (new/full) |

Rationale in one line each: game is temperature-and-front driven (moose most heat-sensitive);
fish are pressure-trend driven with a water-temp suitability gate; ducks are front-and-wind;
walleye is light-and-wind; moon only matters for hare and walleye, per the evidence above.

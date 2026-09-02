# Changelog

## 2.0.0 - in progress

The mod is being rebuilt as a witch-brewing mod. Fuel is gone.

**The model**

- A brew is five essences: four humours on a wheel that rotates as it ages, plus a quintessence that
  is reach rather than character. Amplifier, duration, doses, colour, balance and volatility are all
  derived from the vector rather than stored.
- Ageing is interpolated from the sealed vector, never applied step by step, so a brew keeps its
  shape however old it gets instead of blurring towards grey.
- Ageing is worked out from the seal time, so a flask ages in a chest without anything ticking.

**Brewing**

- The flask holds a sealed brew and gives one dose per sip. Sneak to open the intake.
- A base opens a brew; a cork seals it. What ingredients are worth is a datapack table.
- Tempering against a block changes how fast the brew turns.
- Four vessels. Upgrading re-houses the flask, so mote, seasoning, temper and earned name all carry
  across. Only the Aetherglass lends reach on its own.
- A cracked flask vents its volatile humours first, so it drifts colder and slower as it empties.
  Pack it in sand and fire it to mend it, vessel and all.
- Dregs carry a brew's character and part of its age into the next one; a solera top-up blends both.

**What a brew does**

- Twelve landmarks, generated from the wheel rather than chosen: four humours on their own, four
  leaning into the next humour round, and four carried outward on reach — which is why the four
  reaching brews are the four support roles.
- Twelve draughts, one to a landmark, doing things vanilla effects cannot: flat damage reduction,
  lifesteal, reflected damage, backstab bonuses, damage that scales with your missing health,
  staggering an attacker at range, healing that spills to everyone near you.
- What you get is whichever landmarks the brew is near, in proportion to how near. Hit one squarely
  and you get almost all of one draught; sit between two and you get both; brew something level and
  you get a spread of weak ones and no crash at all.
- The crash is a rebound: four effects of the mod's own, each taking back exactly what its humour
  lent you.
- Combat numbers are configurable, and the server sends the whole config to joining clients.
- EMI pages for the brewing table, tempering, and the twelve known brews, with routes solved from
  the live table so a datapack that retunes an ingredient retunes the page with it.

**In the recipe viewer**

- Every landmark now has a signature ingredient that sits nearer to it than to anything else, so the
  obvious thing to put in Kelpwine is kelp. Added kelp, cactus, glow lichen, fire charge, prismarine
  shard, glistering melon slice, wither skeleton skull, sea pickle, chorus fruit, ender pearl,
  poisonous potato and pufferfish; redstone and glowstone dust carry the same meanings vanilla
  brewing already gives them. Sugar moved to where Speed suggests it should be.
- Suggested routes aim to fill a flask rather than merely to point the right way, and score against
  corruption, so a page telling you how to make something no longer tells you to spoil it.
- The bench operations — corking, the three upgrades, solera and sintering — describe themselves now.
  They are special recipes, which declare no ingredients and no output, so every one of them was
  invisible to a recipe viewer. A new one cannot go missing again: a test fails if any special
  recipe of this mod cannot be drawn.
- Pages for the parts of a flask's life that are not recipes at all: dregs, sump, cracking, ageing.
- Ingredient pages no longer cut themselves off after four lines, which had been hiding corruption on
  exactly the ingredients where it mattered. They say which known brew an ingredient aims at.
- Landmark pages describe a brew in the same words its own tooltip uses, rather than four bare
  numbers, and group a repeated ingredient into one stack.
- Fixed: every synthetic EMI page was registered under an id EMI expected to find in the recipe
  manager, so it logged an error for each one on every world join.

**The Almanac**

- A book, crafted from a book and a flask, opening one map you drag and zoom around rather than a
  set of pages. Hover a node for what it is, click it for the whole entry.
- The map is not drawn by hand where it does not have to be. The twelve known brews lay themselves
  out as the wheel they actually are — angle from the humour that leads them, radius from whether
  reach carries them outward — and the vessel ladder comes from the upgrade recipes. Retune a
  landmark and its node moves.
- Each brew shows the ingredient that means it, found the same way the suggested routes find it, so
  the wheel reads as kelp and cactus and blaze powder rather than twelve identical bottles.
- How to brew, the five axes and all twelve brews are readable from the first minute. The wheel's
  turning, the derivations, the endings and the rarer motes fill in as your palate develops, gated
  on the same Palate that already decides how much a flask's tooltip gives away. Locked nodes still
  hold their place, so the shape of the system is legible before you have earned it.
- The layout is written out by the test suite and drawn by `tools/render_almanac.py`, so the map can
  be looked at without opening the game.

**Art**

- Dregs, sump and the sintered flask redrawn. Dregs and sump were the same two olive tones in
  slightly different outlines, and the sinter read as a picture frame.
- Dregs and sump now take their colour from the brew they remember, the same way the flask takes
  its colour from what is in it — both carried a vector and threw it away at render time. Sump
  keeps the humours it came from, which is also the hook the corrupt half will want.
- The sintered flask is built on the flask's own silhouette, so what is caked in sand is visibly
  the thing you put in.

**Removed**

- The fuel canister mechanic, the furnace mixin, and sparking.

## 1.0.0 - superseded

First release, for Minecraft 1.20.1 on Fabric.

- Empty Cinderflask, crafted from gold and three glass bottles.
- Right-click a mob in `#cinderflask:spark_source` to turn it into a Cinderflask.
- The Cinderflask stores burn ticks and feeds a furnace one smelt at a time, staying in the fuel
  slot as its own recipe remainder.
- One-slot intake screen; sneak + right-click fills from your whole inventory.
- Item texture shows the ember level across five states.
- Config at `config/cinderflask.json`, sent to clients on join.
- EMI category for the sparking step.
- English, Brazilian Portuguese and Spanish.

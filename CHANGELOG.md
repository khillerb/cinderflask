# Changelog

## 2.0.0 - in progress

The mod is being rebuilt as a witch-brewing mod. Fuel is gone.

- A brew is five essences: four humours on a wheel that rotates as it ages, plus a quintessence that
  is reach rather than character. Amplifier, duration, doses, colour, balance and volatility are all
  derived from the vector rather than stored.
- The flask holds a sealed brew and gives one dose per sip. Sneak to open the intake.
- Ageing is worked out from the seal time, so a flask ages in a chest without anything ticking.
- The comedown is whatever sits across the wheel from what you drank, scaled by imbalance.
- Removed: the fuel canister mechanic, the furnace mixin, sparking, and the EMI integration. EMI
  returns once there is something worth showing.

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

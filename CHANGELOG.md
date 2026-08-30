# Changelog

## 1.0.0 - unreleased

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

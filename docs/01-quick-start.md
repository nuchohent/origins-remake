# 01 · Quick Start — make a race without code

This guide is for players who want their own race without touching a single line of code.

## Method A: the in-game Create Race tab (easiest)

1. Join a world (or singleplayer) and press **`G`** (configurable) to open **Race Selection**.
2. Switch to the **Create Race** tab.
3. Fill in:
   - **Name** — e.g. `Frost Giant`.
   - **ID** *(optional)* — e.g. `mypack:frost_giant`. If left empty, one is generated from the name.
   - **Icon item** — any item id, e.g. `minecraft:snow_block`.
   - **Difficulty** — `-5` (very easy / strong) to `+5` (very hard / weak). Negative difficulty = weak race, positive = strong race.
   - **Preview scale** — the model size in the selection preview (visual only).
   - **Description** — what shows on the race card.
4. Under **Powers**, pick a template, give it a display name, tweak its values and press **Add power**. Repeat for each ability.
5. Click **Export to datapack** (singleplayer) — it writes the race into the world's datapacks folder and tells you to run `/reload`. Done!
   - On a server (or if export is not available), click **Copy JSON**, create a datapack (see below), paste the JSON, and reload.

After `/reload` your race appears at the top of the race list.

## Method B: hand-made datapack

A datapack is just a folder (or a `.zip` renamed to `.zip`-free folder) with a `pack.mcmeta`.

```
my_race_pack/
└── pack.mcmeta
└── data/
    └── mypack/
        └── raceapi/
            └── races/
                └── frost_giant.json
```

`pack.mcmeta`:

```json
{
  "pack": {
    "description": "My custom races",
    "pack_format": 48
  }
}
```

`frost_giant.json`:

```json
{
  "display_name": "Frost Giant",
  "description": "Tall and tough, but slow.",
  "icon": "minecraft:snow_block",
  "difficulty": -1,
  "scale": 1.15,
  "powers": [
    { "type": "raceapi:status_effect", "effect": "minecraft:resistance", "amplifier": 0, "duration": 400, "interval": 300 },
    { "type": "raceapi:status_effect", "effect": "minecraft:slowness", "amplifier": 1, "duration": 400, "interval": 300 },
    { "type": "raceapi:toughness", "reduction": 0.3 }
  ]
}
```

### Where to put the pack

- **Singleplayer**: `.minecraft/saves/<world name>/datapacks/my_race_pack/`
- **Server**: `<server folder>/world/datapacks/my_race_pack/`
- Create the folder inside a running game? Just place it and run `/reload`.

### Tips

- `display_name` and `description` are plain text. If you want them translated by language, use `name_key` / `description_key` and add a lang file.
- A race file's `powers` array accepts **power ids** (`"raceapi:dash"`) **or inline objects** (`{ "type": ... }`).
- After changing a file, run `/reload` (requires permission level 2 on servers).
- Wrong file? Check the log (`logs/latest.log`) for `Failed to parse race definition ...` messages.

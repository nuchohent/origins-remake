# 04 · Race templates — copy & paste

Ready-made races you can drop into a datapack. Each template is a single file:
`data/<namespace>/raceapi/races/<name>.json`. Just copy it, optionally tweak the
values, and run `/reload`.

A minimal `pack.mcmeta` next to the `data` folder:

```json
{
  "pack": {
    "description": "OriginsX template races",
    "pack_format": 48
  }
}
```

---

## Titan

Huge health, melee power, slow.

```json
{
  "display_name": "Titan",
  "description": "A giant of stone: enormous health and crushing strength, but ponderous.",
  "icon": "minecraft:stone_block",
  "difficulty": 1,
  "scale": 1.15,
  "powers": [
    { "type": "raceapi:attribute", "attribute": "minecraft:max_health", "amount": 10.0, "operation": "add_value", "difficulty": 2 },
    { "type": "raceapi:attribute", "attribute": "minecraft:attack_damage", "amount": 1.0, "operation": "add_value", "difficulty": 2 },
    { "type": "raceapi:attribute", "attribute": "minecraft:movement_speed", "amount": -0.15, "operation": "add_multiplied_total", "difficulty": -2 }
  ]
}
```

---

## Shadow

Blink through the dark, see at night, fragile.

```json
{
  "display_name": "Shadow",
  "description": "A creature of the night: you blink through shadows and see in the dark, but you are fragile.",
  "icon": "minecraft:black_dye",
  "difficulty": 2,
  "scale": 0.95,
  "powers": [
    { "type": "raceapi:status_effect", "effect": "minecraft:night_vision", "amplifier": 0, "duration": 400, "interval": 300, "difficulty": 2 },
    { "type": "raceapi:blink", "cooldown": 60, "range": 12.0, "display_name": "Shadow Step", "difficulty": 2 },
    { "type": "raceapi:attribute", "attribute": "minecraft:max_health", "amount": -0.3, "operation": "add_multiplied_total", "difficulty": -2 }
  ]
}
```

---

## Frost Giant

Tanky and chilled, with a tough hide.

```json
{
  "display_name": "Frost Giant",
  "description": "Your skin is ice and stone: hits bounce off, but the cold makes you slow.",
  "icon": "minecraft:snow_block",
  "difficulty": 1,
  "scale": 1.1,
  "powers": [
    { "type": "raceapi:toughness", "reduction": 0.3, "difficulty": 3 },
    { "type": "raceapi:status_effect", "effect": "minecraft:resistance", "amplifier": 0, "duration": 400, "interval": 300, "difficulty": 2 },
    { "type": "raceapi:status_effect", "effect": "minecraft:slowness", "amplifier": 1, "duration": 400, "interval": 300, "difficulty": -2 }
  ]
}
```

---

## Wind Runner

Fly, dash and fall softly.

```json
{
  "display_name": "Wind Runner",
  "description": "Master of the skies: fly freely, dash with a gale and never fear the ground.",
  "icon": "minecraft:feather",
  "difficulty": 3,
  "scale": 0.9,
  "powers": [
    { "type": "raceapi:flight", "difficulty": 2 },
    { "type": "raceapi:dash", "cooldown": 40, "strength": 2.2, "display_name": "Gale Dash", "difficulty": 2 },
    { "type": "raceapi:safe_landing", "difficulty": 2 }
  ]
}
```

---

## Swamp Dweller

Breathe underwater, regenerate, but weak in the sun… of the GUI (weakness: none — it's a balanced all-rounder).

```json
{
  "display_name": "Swamp Dweller",
  "description": "At home in the murk: breathe underwater and heal over time, but a soft target.",
  "icon": "minecraft:lily_pad",
  "difficulty": 0,
  "scale": 1.0,
  "powers": [
    { "type": "raceapi:status_effect", "effect": "minecraft:water_breathing", "amplifier": 0, "duration": 400, "interval": 300, "difficulty": 2 },
    { "type": "raceapi:status_effect", "effect": "minecraft:regeneration", "amplifier": 0, "duration": 400, "interval": 300, "difficulty": 2 },
    { "type": "raceapi:attribute", "attribute": "minecraft:max_health", "amount": -0.15, "operation": "add_multiplied_total", "difficulty": -1 }
  ]
}
```

---

## Making your own template

Copy one of the files above and change:

- `display_name` / `description` — plain text shown in the GUI.
- `icon` — any item id.
- `scale` — the preview model size (`0.5` tiny, `1.5` huge).
- `difficulty` — how strong the race feels (`-5` = very strong, `+5` = very weak).
- `powers` — add/remove entries. See `docs/02-datapack-guide.md` for the full list of power types and their fields.

Every power entry can also carry `display_name` and `description` so it reads nicely in the list without a lang file.

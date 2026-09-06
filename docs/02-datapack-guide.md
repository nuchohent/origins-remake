# 02 · Datapack guide — full JSON reference

The Race API loads two kinds of files from datapacks:

- `data/<namespace>/raceapi/races/*.json` — race definitions.
- `data/<namespace>/raceapi/powers/*.json` — reusable power definitions.

Both are re-read on every `/reload`. A Java-registered race or power **always wins** over a JSON one with the same id.

---

## 1. Race file

`data/<namespace>/raceapi/races/<path>.json`

### Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `display_name` | string | file path | Plain-text race name. |
| `name_key` | string | — | If set, a translation key is used instead of `display_name`. |
| `description` | string | `""` | Plain-text description. |
| `description_key` | string | — | If set, a translation key is used instead of `description`. |
| `icon` | string | empty | Item id shown as the icon, e.g. `minecraft:feather`. Empty falls back to the player head. |
| `layer` | string | `"origin"` | Origin layer of the race. Players can have **one race per layer** selected at the same time; races in different layers stack their powers and cosmetics. Layers appear as tabs in the selection GUI. |
| `difficulty` | int | `0` | `-5` (strong) to `+5` (weak). Purely informational. |
| `scale` | float | `1.0` | Preview model scale in the selection GUI (visual only). |
| `width` | double | `0.6` | Bounding box width (informational). |
| `height` | double | `1.8` | Bounding box height (informational). |
| `hidden` | bool | `false` | If `true`, the race is hidden from the GUI (usable via commands only). |
| `powers` | array | `[]` | Each entry is a **power id** string or an **inline power object** (see below). |

### Example

```json
{
  "display_name": "Avian",
  "description": "Light as a feather.",
  "icon": "minecraft:feather",
  "layer": "origin",
  "difficulty": 3,
  "scale": 0.9,
  "width": 0.5,
  "height": 1.7,
  "powers": ["raceapi:creative_flight", { "type": "raceapi:safe_landing", "difficulty": 2 }]
}
```

A second layer (e.g. an "blessing" layer with a decorative flight race) exposes a
second tab in the GUI and lets the player stack a flight race with a "origin" race.

---

## 2. Power file

`data/<namespace>/raceapi/powers/<path>.json`

A standalone power can then be referenced from any race by its id (`<namespace>:<path>`).

### Fields common to all power types

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `type` | string | **required** | One of the power types below. Full id (`raceapi:attribute`) or short path (`attribute`) both work. |
| `difficulty` | int | `0` | **Negative = weakness** (shown red in the GUI), positive = strength (green). |
| `hidden` | bool | `false` | Hide from the GUI. |
| `display_name` | string | — | Overrides the localized name. Without it, the power shows the built-in name (attribute/effect name, or `power.<namespace>.<path>.name` translation key). |
| `description` | string | — | Overrides the description. |

---

## 3. Power types

### `raceapi:attribute`
Applies an attribute modifier while the race is active.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `attribute` | string | — | Attribute id, e.g. `minecraft:max_health`, `minecraft:movement_speed`, `minecraft:attack_damage`, `minecraft:armor`. |
| `amount` | double | `1.0` | Modifier amount. |
| `operation` | string | `add_value` | `add_value` (flat), `add_multiplied_base` (percent of base), `add_multiplied_total` (percent of total). |

Examples:
```json
{ "type": "raceapi:attribute", "attribute": "minecraft:max_health", "amount": 4.0, "operation": "add_value", "difficulty": 2 }
{ "type": "raceapi:attribute", "attribute": "minecraft:movement_speed", "amount": -0.2, "operation": "add_multiplied_total", "difficulty": -2 }
```

### `raceapi:status_effect`
Re-applies a potion effect while active.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `effect` | string | — | Effect id, e.g. `minecraft:speed`. |
| `amplifier` | int | `0` | Effect level (0 = level I). |
| `duration` | int | `400` | Duration of each application, in ticks (20 ticks = 1 second). |
| `interval` | int | `300` | How often it is re-applied, in ticks. |

Example:
```json
{ "type": "raceapi:status_effect", "effect": "minecraft:regeneration", "amplifier": 0, "duration": 400, "interval": 300, "difficulty": 2 }
```

### `raceapi:flight`
Creative-style flight.

| Field | Type | Default |
| --- | --- | --- |
| `difficulty` | int | `2` |

### `raceapi:high_jump`
Boosts jump height.

| Field | Type | Default |
| --- | --- | --- |
| `boost` | double | `0.55` (vertical jump velocity added) |

### `raceapi:dash`
Active power: dash forwards along your look direction (bound to `Use Active Power`).

| Field | Type | Default |
| --- | --- | --- |
| `cooldown` | int | `60` (ticks) |
| `strength` | double | `1.8` |

### `raceapi:blink`
Active power: teleport a short distance in the direction you look.

| Field | Type | Default |
| --- | --- | --- |
| `cooldown` | int | `80` (ticks) |
| `range` | double | `10.0` (blocks) |

### `raceapi:creative_flight`
Grants creative flight (`mayfly`) while the race is selected. Removed when the race
is cleared, re-asserted every tick.

| Field | Type | Default |
| --- | --- | --- |
| `difficulty` | int | `1` |

### `raceapi:safe_landing`
No fall damage.

| Field | Type | Default |
| --- | --- | --- |
| `difficulty` | int | `2` |

### `raceapi:toughness`
Reduces incoming damage by a fraction.

| Field | Type | Default |
| --- | --- | --- |
| `reduction` | double | `0.25` (`0.3` = 30% less damage) |

---

### `raceapi:conditional`

Wraps another power so it only works while a **condition** holds. While the
condition is false, the optional `negate_power` runs instead. This is how you
build "strong at night, weak during the day"-style races.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `condition_type` | string | required | One of the condition types from the table below. |
| ... | | | Extra fields depend on the chosen condition type (see table). |
| `condition_label` | string | condition type | Human-readable label shown in tooltips. |
| `power` | object | required | Inner power, active while the condition is true. |
| `negate_power` | object | — | Optional power, active while the condition is false. |

```json
{
  "type": "raceapi:conditional",
  "condition_type": "is_night",
  "condition_label": "At night",
  "power": { "type": "raceapi:attribute", "attribute": "minecraft:attack_damage", "amount": 4 },
  "negate_power": { "type": "raceapi:status_effect", "effect": "minecraft:weakness", "duration": 5 }
}
```

#### Condition types

Boolean states (no extra fields):

| `condition_type` | True while… |
| --- | --- |
| `in_water` / `not_in_water` | player is submerged / dry |
| `eye_in_water` | water reaches the player's eyes |
| `on_ground` / `in_air` | standing on ground / airborne |
| `is_day` / `is_night` | overworld daytime / night |
| `is_sprinting` / `is_crouching` | sprinting / crouching |
| `is_swimming` | actively swimming |
| `climbing` | climbing a ladder or vine |
| `is_on_fire` | burning |
| `is_falling` | falling |
| `is_raining` | raining at player position |
| `thundering` | thunderstorm at player position |
| `has_armor` | wearing at least one armor piece |
| `is_full_health` | health bar is full |

Thresholds:

| `condition_type` | Fields | True while… |
| --- | --- | --- |
| `health_below` / `health_above` | `threshold` 0.0–1.0 (default `0.5`) | health fraction below/above threshold |
| `hunger_below` / `hunger_above` | `threshold` 0–20 (default `10`) | food points below/above threshold |
| `air_below` / `air_above` | `threshold` 0.0–1.0 (default `0.5`) | breath fraction below/above threshold |
| `xp_level_below` / `xp_level_above` | `threshold` int (default `0`) | XP level below/above threshold |
| `light_level_below` / `light_level_above` | `threshold` 0–15 (default `7`) | effective light below/above threshold |
| `below_y` / `above_y` | `threshold` int | Y coordinate below/above value |
| `armor_count_above` / `armor_count_below` | `threshold` 0–4 (default `1`) | worn armor pieces above/below count |
| `entities_nearby_above` / `entities_nearby_below` | `radius` (default `8`), `threshold` (default `1`), optional `entity` id or `entity_tag`, `include_self` (default `false`) | living entity count within radius above/below threshold |

World & location:

| `condition_type` | Fields | True while… |
| --- | --- | --- |
| `in_biome` | `tag` biome tag | biome matches tag |
| `biome_id` | `biome` biome id | exact biome match |
| `in_dimension` | `dimension` dimension id | dimension match |
| `standing_on` | `block` id or `block_tag` | block under feet matches |
| `time_between` | `min` (default `0`), `max` (default `24000`) | overworld clock ticks in `[min, max)` |
| `moon_phase` | `phase`: `full_moon`, `waning_gibbous`, `third_quarter`, `waning_crescent`, `new_moon`, `waxing_crescent`, `first_quarter`, `waxing_gibbous` | current moon phase matches |
| `gamemode` | `mode`: `survival`, `creative`, `adventure`, `spectator` | game mode matches |
| `riding` | optional `entity` type id | riding anything / a specific mount |

Items & effects:

| `condition_type` | Fields | True while… |
| --- | --- | --- |
| `held_item` / `offhand_item` | `item` id or `item_tag` (e.g. `minecraft:swords`) | matching item in main/off hand |
| `wearing_item` | `slot`: `head`/`chest`/`legs`/`feet`, plus `item` or `item_tag` | matching armor piece worn |
| `has_effect` | `effect` effect id | player has the potion effect |
| `scoreboard_above` / `scoreboard_below` | `objective` name, `threshold` (default `0`) | scoreboard score above/below threshold |

Logic combinators:

| `condition_type` | Fields | Description |
| --- | --- | --- |
| `not` | `inner` object | Negates the inner condition. |
| `all_of` | `conditions` array | All sub-conditions must hold. |
| `any_of` | `conditions` array | At least one sub-condition holds. |

Example of a nested composite — "vampire" build: strong at night while holding
a sword, but only above ground:

```json
{
  "type": "raceapi:conditional",
  "condition_type": "all_of",
  "condition_label": "Night hunter",
  "power": { "type": "raceapi:attribute", "attribute": "minecraft:movement_speed", "amount": 0.03 },
  "negate_power": { "type": "raceapi:status_effect", "effect": "minecraft:slowness", "duration": 5 },
  "conditions": [
    { "condition_type": "is_night" },
    { "condition_type": "held_item", "item_tag": "minecraft:swords" },
    { "condition_type": "not", "inner": { "condition_type": "below_y", "threshold": 50 } }
  ]
}
```

---

## 4. Inline powers

Powers don't have to be separate files. Any entry in a race's `powers` array may be an object:

```json
{
  "display_name": "My Race",
  "powers": [
    "raceapi:creative_flight",
    { "type": "raceapi:dash", "cooldown": 40, "strength": 2.2, "display_name": "Gale Dash" }
  ]
}
```

Inline powers are given a synthetic id (`<raceid>_power_0`, `_power_1`, ...) so attribute modifiers and cooldowns stay stable across reloads.

---

## 5. Translation keys

If you don't set `display_name` on a power, its name is looked up from:

- attribute / effect powers → the vanilla localized attribute / effect name (e.g. "Max Health", "Speed");
- all other types → `power.<namespace>.<path>.name` (and `.desc` for the description).

Ship your own lang file in the datapack:

```
data/mypack/lang/en_us.json
```

```json
{
  "power.mypack.gale_dash.name": "Gale Dash",
  "power.mypack.gale_dash.desc": "Launch yourself forward.",
  "race.mypack.frost_giant.name": "Frost Giant",
  "race.mypack.frost_giant.desc": "Tall and tough, but slow."
}
```

And reference them from the race:

```json
{
  "name_key": "race.mypack.frost_giant.name",
  "description_key": "race.mypack.frost_giant.desc",
  "powers": [
    { "type": "raceapi:dash", "cooldown": 40, "difficulty": 1, "display_name": "Gale Dash" }
  ]
}
```

---

## 6. Built-in powers (reference them by id, no file needed)

| Id | Effect |
| --- | --- |
| `raceapi:creative_flight` | Creative flight |
| `raceapi:water_breathing` | Water Breathing |
| `raceapi:night_vision` | Night Vision |
| `raceapi:speed` | Speed II |
| `raceapi:jump_boost` | Jump Boost II |
| `raceapi:haste` | Haste II |
| `raceapi:regeneration` | Regeneration I |
| `raceapi:strength` | Strength I |
| `raceapi:resistance` | Resistance I |
| `raceapi:fire_resistance` | Fire Resistance I |
| `raceapi:max_health_plus` | +4 max health |
| `raceapi:max_health_minus` | -4 max health |
| `raceapi:slow` | -20% movement speed |
| `raceapi:frail` | -40% max health (hidden) |
| `raceapi:dash` | Gale Dash (active) |
| `raceapi:blink` | Shadow Blink (active) |
| `raceapi:high_jump` | Leap |
| `raceapi:safe_landing` | Feather Fall |
| `raceapi:toughness` | Tough Hide |
| `raceapi:slowness` | Slowness II (weakness) |
| `raceapi:mining_fatigue` | Mining Fatigue II (weakness) |

---

## 7. Troubleshooting

- **Race doesn't show up after `/reload`** → check `logs/latest.log` for `Failed to parse race definition` or `Unknown power`.
- **Race shows but has no name** → you used `name_key` without a matching lang entry.
- **Power shows a raw key like `power.mypack.x.name`** → the type has no built-in name (dash, blink, ...). Add `display_name` or a lang file.
- **JSON file has an inline power that failed** → check the log for `Invalid inline power #N in race`.

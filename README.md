# Origins Remake

A Minecraft **26.2 (NeoForge)** project that brings an Origins-style race system to the game.

## Modules

| Module | Description |
| --- | --- |
| **OriginsX** | The main mod. Adds the race selection GUI, keybinds, and the **no-code Race Creator** tab. |
| **Race API** | The library mod behind everything. Defines `Race` / `Power`, loads JSON datapack races, and powers the runtime effects. OriginsX depends on it. |

> The `Default Project` folder is an unrelated fancyhud starter template — ignore it.

## What you can do

- Pick a race from a GUI (`Open Race Selection` key, default `K`). Each race shows an icon, difficulty, description, a live animated preview of your skin, and its powers.
- **Create your own race without writing any code**: open the `Create Race` tab, fill in a name, icon, difficulty, description, add powers from templates, and either copy the JSON or export it straight into the world's datapacks. Run `/reload` and your race appears.
- Build full datapacks with custom races and custom powers (see `docs/`).
- Write your own races and powers in Java if you are a modder (see `docs/03-java-api.md`).

## Installation

1. Install Minecraft **26.2** with **NeoForge 26.2.0.67**.
2. Drop **Race API** and **OriginsX** into the `mods` folder.
3. (Optional) Add **LDLib2** (the GUI framework OriginsX uses).

## Controls

| Key | Action |
| --- | --- |
| `G` (configurable) | Open Race Selection |
| `V` (configurable) | Use Active Power (for races with a bound ability, marked `[B]`) |

## Commands

- `/raceapi list` — list all registered races.
- `/raceapi set <player> <namespace:path>` — set a player's race (op).
- `/raceapi clear <player>` — remove a player's race (op).
- `/originsx set <player> <namespace:path>` / `/originsx clear <player>` — same, from OriginsX.
- `/originsx open` — client command that opens the selection screen.

## Documentation

- `docs/01-quick-start.md` — make your first race without code (players).
- `docs/02-datapack-guide.md` — full JSON reference for races and powers.
- `docs/03-java-api.md` — API guide for modders.
- `docs/04-templates.md` — copy-paste race templates.

## License

All code is provided as-is for personal and modpack use.

# Race API 0.7.5 — Changelog

Хотфикс к OriginsX 1.10.8. OriginsX и Skill Tree не менялись (зависимость raceapi минимум 0.7.0 — совместимо).

## RU

### Исправлено
- **Условие света работало неправильно** — `light_level_below/above` не учитывал затемнение неба: ночью на поверхности свет читался как 15, и «в темноте» не срабатывало после заката. Теперь берётся реальный свет с учётом времени суток и погоды.
- **Падение при загрузке рас с предметными условиями** — NPE «Components not bound yet» при парсинге `held_item`/`offhand_item` (название предмета теперь берётся через ключ перевода, без создания ItemStack на ранней фазе загрузки).
- **`in_biome` не принимал тег с решёткой** — `#minecraft:is_forest` теперь работает так же, как `minecraft:is_forest`.

### Добавлено
- **+24 новых типа условий** (всего 50):
  - Предметы: `held_item`, `offhand_item`, `wearing_item`, `armor_count_above/below`
  - Состояние: `hunger_below/above`, `air_below/above`, `xp_level_above/below`, `climbing`, `eye_in_water`, `riding`, `gamemode`
  - Мир: `standing_on` (блок/тег блока), `biome_id`, `moon_phase` (8 фаз), `thundering`, `time_between`
  - RPG: `entities_nearby_above/below` (радиус + порог + фильтр по мобу/тегу), `scoreboard_above/below`
- **Условия переведены** — метка условной силы больше не сырой айди: «Ночью», «Держа Алмазный меч», «Фаза луны: Полнолуние», композиты склеиваются («НОЧЬЮ и Держа #minecraft:swords»). 59 ключей локализации для en_us / ru_ru / uk_ua. Свой `condition_label` приоритетнее автоподписи.
- **Документация** — в гайд датапака добавлена секция про `raceapi:conditional` с таблицами всех 50 условий и примерами.

## EN

### Fixed
- **Light condition was wrong** — `light_level_below/above` ignored sky darkening: at night the surface read as light 15, so "in darkness" never triggered after sunset. Now uses real effective light (time of day + weather).
- **Crash when loading races with item conditions** — NPE "Components not bound yet" while parsing `held_item`/`offhand_item` (item names now resolved via translation keys, no early-phase ItemStack construction).
- **`in_biome` rejected tags with a leading `#`** — `#minecraft:is_forest` now works the same as `minecraft:is_forest`.

### Added
- **24 new condition types** (50 total):
  - Items: `held_item`, `offhand_item`, `wearing_item`, `armor_count_above/below`
  - State: `hunger_below/above`, `air_below/above`, `xp_level_above/below`, `climbing`, `eye_in_water`, `riding`, `gamemode`
  - World: `standing_on` (block/block tag), `biome_id`, `moon_phase` (all 8 phases), `thundering`, `time_between`
  - RPG: `entities_nearby_above/below` (radius + threshold + entity/tag filter), `scoreboard_above/below`
- **Conditions are now translated** — conditional power labels are no longer raw ids: "At night", "Holding Diamond Sword", "Moon phase: Full Moon", composites join recursively ("AT NIGHT and Holding #minecraft:swords"). 59 localization keys for en_us / ru_ru / uk_ua. An explicit `condition_label` still takes priority.
- **Documentation** — the datapack guide now covers `raceapi:conditional` with reference tables for all 50 condition types and examples.

# third_party — завендоренные compile-only зависимости

Эти jar'ы **нужны для сборки сателлитов** (`compileOnly fileTree(dir: 'libs')`),
но не публикуются в maven. Положены сюда, чтобы проект собирался из коробки без
внешних скачиваний. В jar'ы модов НЕ встраиваются (jarJar не используется).

| Jar | Почему здесь | Лицензия |
| --- | --- | --- |
| `fancytabsections-6.0-NEOFORGE-26.2.jar` | OriginsX/SkillTree/Looks компилируются против `net.mcexpanded.fancytabsections.*` (секции креативных вкладок). Подключается только в dev-сборке; в рантайме мод опционален (`ModList.isLoaded("fancytabsections")`). | **MIT** — авторы: wdiscute, Kaupenjoe, nanoattack (см. `META-INF/neoforge.mods.toml` внутри jar) |

Версия: `6.0-NEOFORGE-26.2`, оригинал — с CurseForge/Modrinth «Fancy Tab Sections».

> `*/libs/*.jar` в `.gitignore` (исключение — `third_party/`): локальные либы
> пересобираются при каждом build из `RaceAPI/build/libs` + этого каталога.
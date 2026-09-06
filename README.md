# OriginsX / Race API / Skill Tree / Looks

**Origins-style race system for Minecraft 26.2 (NeoForge 26.2.0.67).**
*Система рас в духе Origins: выбор расы, создатель рас без кода, деревья навыков и косметика.*

This repository is a **handover kit**: source code + the author's notes, dev logs, patch logs and
statuses are all committed so anyone can keep the project going. Start with `STATUS.md`, then
`PATCH_LOG.md`.

---

## Что это за проект (это 4 мода)

| Модуль | Директория | Версия | Роль |
| --- | --- | --- | --- |
| **Race API** | `RaceAPI/` | **2.0.0** | Библиотека-ядро. Загружает расы из JSON-датапаков, определяет `Race`/`Power`, исполняет эффекты рас, синкает выбор по сети, команды `/raceapi`. Всё остальное зависит от неё. |
| **OriginsX** | `OriginsX/` | **2.0.0** | Главный мод. GUI выбора расы, HUD, клавиши, и **no-code создатель рас** (вкладка Create Race), шаринг рас (строки `ORX1:...`), импорт. |
| **Skill Tree** | `SkillTree/` | **2.0.0** | Аддон. Деревья навыков для рас: узлы — силы расы, осколки — валюта прокачки. Редактор деревьев в игре. |
| **Looks** | `Looks/` | **1.0.1-alpha** | Аддон-косметика. Крепление предметов к костям персонажа, живой Blender-style редактор, пресеты, анимация, превью на себе. Пока alpha. |

**Граф зависимостей:** `OriginsX → Race API`, `Skill Tree → Race API`, `Looks → Race API`.
Все три требуют Race API **2.0.0+**. GUI всех модов построен на **LDLib2** (`ldlib2_version = 26.2.2.35`).

> Окончательная правда о версиях — в `gradle.properties` каждого модуля (`mod_version`).
> Краткие сводки версий и «что нового» — в `STATUS.md` и `PATCH_LOG.md`.

## 💡 Куда смотреть, чтобы продолжить работу

| Файл | Что это |
| --- | --- |
| `STATUS.md` | **Передача дел**: текущее состояние, собранные jar'ы, список «осталось на след. сессию», конвенции, замечания среды. ОБНОВЛЯТЬ ПЕРВЫМ делом. |
| `PATCH_LOG.md` | Полная история изменений по всем модулям (самая свежая запись вверху). |
| `devlog.txt` | Ранний дневник разработки (19.08–20.08). |
| `TESTING_CHECKLIST.md` | Чеклист ручного тестирования всех 52+ способностей. Автор считал устаревшим — можно переписать под текущие реалии. |
| `LOOKS_AUDIT_2026-08-27.md` | Аудит модуля Looks: 15 находок, все закрыты к 2026-08-29. |
| `docs/` | Гайды для пользователей и моддеров: `01-quick-start`, `02-datapack-guide`, `03-java-api`, `04-templates`. |
| `RaceAPI/logs/*.log.gz` | Runtime-логи дев-запусков (для истории). |
| `OriginsX/build_out.log` / `build_err.log` | Логи последних сборок. |
| `datapacks/gift_starter_races/` | Подарочный набор из 5 рас (можно развивать в официальный контент или удалить). |

## 🚀 Быстрый старт (игрок/модпак)

1. Установи **Minecraft 26.2** + **NeoForge 26.2.0.67+**.
2. Положи в `mods/` jar'ы **Race API** и **OriginsX** (из `*/build/libs/` после сборки, см. ниже).
3. (Опционально) **LDLib2** — фреймворк GUI; страховочный jar `fancytabsections` для вкладок.
4. Запусти. При первом заходе автоматически откроется экран выбора расы.

### Управление (настраивается)

| Клавиша | Действие |
| --- | --- |
| `G` | Открыть выбор расы (по умолч.) |
| `V` | Использовать активную силу (до 9 слотов, слоты с меткой `[B]`) |
| `O` | HUD расы |
| `J` | Настройки HUD |
| `Ctrl+L` | Режим раскладки редактора внешности (Looks) |

### Команды

- `/raceapi list` — список рас
- `/raceapi set <player> <ns:path>` / `/raceapi clear <player> [layer]` — выбор/сброс расы (op)
- `/originsx set <player> <ns:path>` / `/originsx clear <player>` — то же из OriginsX
- `/originsx open` — открыть экран выбора (клиент)
- `/looks reload`, `/looks debug` — перезагрузка/диагностика косметики

## 🧱 Сборка из исходников

Каждый модуль — отдельный Gradle-проект NeoForge:

```bash
gradle -p RaceAPI build   # сначала ядро
gradle -p OriginsX build  # потом сателлиты
gradle -p SkillTree build
gradle -p Looks build
```

`BUILD SUCCESSFUL` по 4 модулям подтверждался в PATCH_LOG (сборка 2026-09-05).

**Требования и грабли среды (важно):**

- **JDK 25** (toolchain 25; системный JDK 26 не подходит). Рабочий путь прописан в
  `RaceAPI/gradle.properties` → `org.gradle.java.installations.paths` — вставь свой
  путь к JDK 25 или положи JDK 25 туда, где Gradle найдёт его сам.
- **`gradlew` в корне/модулях может не работать** (битый `gradle-wrapper.jar` без
  Main-Class по состоянию на 2026-09-05). Запасной вариант — локально установленный
  **Gradle 9.2.1**: `~/.gradle/wrapper/dists/gradle-9.2.1-bin/…/bin/gradle`.
- Модули компилируются против JDK-совместимого класса RaceAPI из `libs/` сателлитов
  (`compileOnly`) — после сборки RaceAPI переложить свежий jar в `libs/` трёх сателлитов,
  если меняешь API.
- Локальные пути `runs/`, `build/`, `.gradle/`, `bin/` и распакованные источники
  (`net/`, `com/`) в git не попадают — см. `.gitignore`. Разработчикам сюда
  **не пушить** дикомпилированный код Minecraft/LDLib2 (лицензия Mojang/LowDragMC).

## 📖 Где что лежит в каждом модуле

```
RaceAPI/src/main/java/dev/raceapi/     — race, power, conditions, команды, сеть
RaceAPI/src/test/java/dev/raceapi/     — юнит-тесты (27/27 на момент релиза 1.0.0)
RaceAPI/src/main/resources/data/…      — встроенные демо-расы (arachnid, phantom, …)
OriginsX/src/main/java/dev/originsx/   — GUI, создатель рас, HUD, шаринг, сеть
SkillTree/src/main/java/dev/originsx/  — дерево навыков, редактор, SkillGate, конфиг
Looks/src/main/java/dev/originsx/      — косметика, рендер-слои, редактор, миксины
docs/                                  — гайды JSON/Java (рекомендую читать первым)
```

## 💬 Как участвовать

- Сначала прочитай `STATUS.md` + `PATCH_LOG.md` — там записаны **конвенции, которые нельзя
  нарушать**: семвер (мажор только при ломании совместимости), баланс (минус = бафф,
  плюс = дебафф), канал выбирается на CF и в строке версии не пишется.
- Изменения API Race → минимум **минор**, ломающие → **мажор**; сателлиты поднимают
  `raceapi_version` соответственно.
- Новые правки: сначала код, потом запись в `PATCH_LOG.md`, обновление `STATUS.md`,
  пересборка всех 4 модулей, РУЧНОЙ прогон по чеклисту.
- Вся история работы — в коммитах; структура коммитов: `fix(...)`, `feat(...)`,
  `docs: ...`.

## 📜 Лицензия

Каждый модуль объявляет **MIT** в `gradle.properties` (`mod_license`). Код в этом репо —
исходники автора; дикомпилированный код Minecraft/LDLib2 в репозиторий намеренно не
включался.
# Contributing to OriginsX / Race API / Skill Tree / Looks

Спасибо, что решил помочь. Это руко́водство написано в том же духе, что и `STATUS.md` —
**сначала прочитай оба файла**: `STATUS.md` (текущее состояние + конвенции) и
`PATCH_LOG.md` (вся история изменений). Ниже — минимум, чтобы не сломать проект.

## Структура

```
RaceAPI/   — ядро. НЕ ломать API без мажора (2.0.0 → 3.0.0).
OriginsX/  — главный мод, зависит от Race API (compileOnly, jar в libs/).
SkillTree/ — дерево навыков, сателлит Race API.
Looks/     — косметика, сателлит Race API (alpha).
docs/      — гайды по JSON/Java API.
```

## Конвенции (не нарушать)

- **Семвер**: мажор (x.0.0) — только при ломающем изменении совместимости (формат
  датапаков, Java API, сетевая схема). Минор/патч — всё остальное. Канал
  (alpha/release) выбирается на CurseForge, в строке версии его НЕТ.
- **Баланс**: `difficulty < 0` = бафф (сильная), `> 0` = дебафф (слабость).
  Веса типов — `POWER_TYPE_WEIGHTS` в `RaceCreatorPanel`, метки характера —
  через `powerWeight()`.
- **Инлайн-силы** из JSON получают авто-id `<раса>_power_<n>` — тип брать из класса.
- **Эффекты рас** помечаются тегами `raceapi_eff_*` и не затирают зелья игрока.
- **Грант-флаги** стартовых предметов переживают снятие расы (анти-рефарм).
- Если трогаешь API Race — подними `raceapi_version` в `gradle.properties`
  сателлитов и минимальную версию в их `mods.toml`.
- Никогда не коммить дикомпилированный код (Minecraft/LDLib2). См. `.gitignore`.

## Сборка

Требуется **JDK 25** (toolchain). `JAVA_HOME` на JDK 25 достаточно; захардкоженные
пути из `gradle.properties` убраны намеренно.

```bash
./gradlew -p RaceAPI build          # сначала ядро
cp RaceAPI/build/libs/"Race API-26.2-"*.jar "не-sources" → OriginsX/libs/, SkillTree/libs/, Looks/libs/
./gradlew -p OriginsX build
./gradlew -p SkillTree build
./gradlew -p Looks build
```

> На GitHub Actions это делает CI (`./.github/workflows/build.yml`) — jar'ы попадают
> в артефакты workflow, а по тегам `v*` публикуются в GitHub Release.

## Как вносить изменения

1. Важно! Сначала задача, потом код. Коммит-стиль репозитория:
   `fix(...)`, `feat(...)`, `docs: ...`, `release: ...`.
2. После правки кода — запиши её в `PATCH_LOG.md` (сверху, по образцу).
3. Обнови `STATUS.md` (что закрыто, что осталось, версии).
4. Пересобери все 4 модуля (см. выше).
5. Прогони ручной чеклист по `TESTING_CHECKLIST.md` (по крайней мере Smoke-тест:
   зайти в мир, открыть выбор расы, выбрать расу, перезайти).
6. Тесты Race API: `./gradlew -p RaceAPI test` (JUnit 5, на момент релиза 27/27).
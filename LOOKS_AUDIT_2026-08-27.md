# 🔍 АУДИТ МОДУЛЯ LOOKS — Полный отчёт
**Дата:** 2026-08-27  
**Проверено файлов:** 15 Java + 6 ресурсов  
**Категории:** Критические баги, Средние проблемы, Мелкие улучшения

---

## ⚠️ КРИТИЧЕСКИЕ БАГИ

### 🔴 КРИТИЧЕСКИЙ #1: Статический метод в миксине (EntityAimMixin.java:21)
**Файл:** `EntityAimMixin.java:21`  
**Проблема:**
```java
@Redirect(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
private static float looks$aimFromMobHead(Entity entity) {
    float mobEye = EntityForm.formEyeHeightFor(entity);
    return mobEye >= 0.0F ? mobEye : entity.getEyeHeight();
}
```

**Почему это баг:**  
Метод объявлен как `static`, но `@Redirect` в миксинах **не может перехватывать вызовы инстанс-методов через статические методы**. Метод `Entity.getEyeHeight()` — инстанс-метод, поэтому редирект должен быть **НЕстатическим**.

**Последствия:**  
- Редирект не срабатывает
- Прицеливание и рейкаст остаются на обычной высоте игрока
- Когда игрок превращён в маленького моба (например, аксолотля), камера сидит низко, но прицел остаётся на высоте головы обычного игрока — **полная рассинхронизация камеры и прицела**

**Исправление:**
```java
@Redirect(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
private float looks$aimFromMobHead(Entity entity) {  // убрать static
    float mobEye = EntityForm.formEyeHeightFor(entity);
    return mobEye >= 0.0F ? mobEye : entity.getEyeHeight();
}
```

---

### 🔴 КРИТИЧЕСКИЙ #2: Утечка памяти через статическую WeakHashMap (EntityForm.java:60)
**Файл:** `EntityForm.java:60-61`  
**Проблема:**
```java
private static final java.util.Set<EntityType<?>> crownedTypes =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
```

**Почему это баг:**  
`WeakHashMap` хранит **ключи как weak references**, но `Collections.newSetFromMap()` использует **ключи как элементы сета**. Проблема: `EntityType<?>` — это **синглтоны из регистра**, на которые всегда есть сильные ссылки из `BuiltInRegistries.ENTITY_TYPE`. Weak-ключи никогда не GC'атся → **WeakHashMap работает как обычная HashMap** → накопление типов **без очистки**.

**Последствия:**  
- При частом пересоздании ghost-сущностей (переключение рас, респаун) накапливаются типы
- Память растёт, рендереры дублируются
- После 100+ переключений рас возможны **падения FPS** из-за множества дублированных layer'ов

**Исправление:**
```java
// Вариант 1: обычный HashSet (типы — синглтоны, утечки нет)
private static final java.util.Set<EntityType<?>> crownedTypes = new java.util.HashSet<>();

// Вариант 2: ConcurrentHashMap.newKeySet() для thread-safety
private static final java.util.Set<EntityType<?>> crownedTypes = 
        java.util.concurrent.ConcurrentHashMap.newKeySet();
```

---

### 🔴 КРИТИЧЕСКИЙ #3: Race condition в EntityForm.update() (EntityForm.java:141-177)
**Файл:** `EntityForm.java:141-177`  
**Проблема:**
```java
private static void update() {
    // ...
    if (ghost == null || ghost.isRemoved() || ghost.getType() != type) {
        discardGhost();
        ghost = createGhost(level, type);  // ← ASYNC создание
        if (ghost == null) {
            return;
        }
        currentType = type;
        currentHeight = ghost.getDimensions(ghost.getPose()).height();
        currentEyeHeight = ghost.getEyeHeight();
        registerCrownLayer(ghost);  // ← может вызваться ДО того, как ghost добавится в level
    }
    syncGhost(player);
}
```

**Почему это баг:**  
`update()` вызывается **дважды за тик** (Pre и Post), `createGhost()` может вернуть объект, но добавление в `level.addEntity()` (строка 223) **асинхронное**. Если между Pre и Post:
1. Pre создал ghost
2. Ghost ещё не в world
3. Post вызывает `syncGhost()` → обращается к полям ghost **до его инициализации** → NPE или некорректные данные

**Последствия:**  
- Случайные NPE при быстром переключении рас
- Ghost рендерится на 1-2 фрейма в (0,0,0)
- Десинхронизация позиции ghost и игрока

**Исправление:**
```java
private static void update() {
    // ...
    if (ghost == null || ghost.isRemoved() || ghost.getType() != type) {
        discardGhost();
        ghost = createGhost(level, type);
        if (ghost == null) {
            return;
        }
        currentType = type;
        currentHeight = ghost.getDimensions(ghost.getPose()).height();
        currentEyeHeight = ghost.getEyeHeight();
        registerCrownLayer(ghost);
    }
    // GUARD: не синкать пока ghost не готов
    if (ghost != null && !ghost.isRemoved()) {
        syncGhost(player);
    }
}
```

---

## 🟠 СРЕДНИЕ ПРОБЛЕМЫ

### 🟡 СРЕДНИЙ #1: Отсутствие null-check в CosmeticsLayer.submit() (CosmeticsLayer.java:76-79)
**Файл:** `CosmeticsLayer.java:76-79`  
**Проблема:**
```java
PlayerModel model = getParentModel();
for (CosmeticsStateModifier.Extracted data : extracted) {
    ModelPart bone = bone(model, data.entry().part());
    if (bone == null) {
        continue;
    }
```

**Почему проблема:**  
`getParentModel()` может вернуть `null` если рендерер не `LivingEntityRenderer<?, PlayerModel>`. Дальше `bone(model, ...)` вызовется с `null` → **NPE**.

**Последствия:**  
- Краш при попытке нарисовать косметику на нестандартном рендерере (модифицированная модель игрока из другого мода)

**Исправление:**
```java
PlayerModel model = getParentModel();
if (model == null) {
    return;  // нет модели → нечего рендерить
}
for (CosmeticsStateModifier.Extracted data : extracted) {
    ModelPart bone = bone(model, data.entry().part());
    // ...
}
```

---

### 🟡 СРЕДНИЙ #2: Verbose logging в продакшене (CameraMixin.java:31)
**Файл:** `CameraMixin.java:31-32`  
**Проблема:**
```java
LooksMod.LOGGER.info("[Looks] camera eye -> mob {} for {}",
        mobEye, player.getName().getString());
```

**Почему проблема:**  
Лог пишется **каждый тик** пока игрок трансформирован (60 раз в секунду) → **спам в консоли**, замедление I/O.

**Исправление:**
```java
// Вариант 1: перенести на debug
LooksMod.LOGGER.debug("[Looks] camera eye -> mob {} for {}", mobEye, player.getName().getString());

// Вариант 2: throttle через tick counter
private static int logThrottle = 0;
// ...
if (mobEye > 0.0F) {
    if (logThrottle++ % 100 == 0) {  // раз в ~1.6 сек
        LooksMod.LOGGER.info("[Looks] camera eye -> mob {}", mobEye);
    }
    return mobEye;
}
```

---

### 🟡 СРЕДНИЙ #3: Неоптимальная проверка дистанции в EntityCrownLayer (EntityCrownLayer.java:84-88)
**Файл:** `EntityCrownLayer.java:84-88`  
**Проблема:**
```java
float dx = (float) state.x - (float) mc.player.getX();
float dy = (float) state.y - (float) mc.player.getY();
float dz = (float) state.z - (float) mc.player.getZ();
if (dx * dx + dy * dy + dz * dz > 0.05f) {
    return;
}
```

**Почему проблема:**  
Порог `0.05` — это **0.05 блока² = ~0.22 блока**. Слишком жёсткий → если ghost хоть чуть-чуть отстал от игрока (интерполяция, лаг), косметика пропадает.

**Исправление:**
```java
// 0.5 блока² = ~0.7 блока — разумный порог с запасом
if (dx * dx + dy * dy + dz * dz > 0.25f) {
    return;
}
```

---

### 🟡 СРЕДНИЙ #4: Потенциальный ClassCastException в EntityForm.registerCrownLayer() (EntityForm.java:259)
**Файл:** `EntityForm.java:254-260`  
**Проблема:**
```java
net.minecraft.client.renderer.entity.EntityRenderer<? super net.minecraft.world.entity.Entity, ?>
        renderer = mc.getEntityRenderDispatcher().getRenderer(living);
if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> ler)) {
    return;
}
((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?>) ler)
        .addLayer(new EntityCrownLayer<>((net.minecraft.client.renderer.entity.RenderLayerParent) ler, type));
```

**Почему проблема:**  
После проверки `instanceof LivingEntityRenderer` делается **unchecked cast** к `RenderLayerParent` без проверки. Не все `LivingEntityRenderer` реализуют `RenderLayerParent` (хотя в ванилле да).

**Исправление:**
```java
if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> ler)) {
    return;
}
if (!(ler instanceof net.minecraft.client.renderer.entity.RenderLayerParent<?, ?> parent)) {
    return;
}
parent.addLayer(new EntityCrownLayer<>(parent, type));
```

---

### 🟡 СРЕДНИЙ #5: Отсутствие валидации в LooksScreen.applyNumberField() (LooksScreen.java:891-920)
**Файл:** `LooksScreen.java:909-916`  
**Проблема:**
```java
case "scale" -> {
    try {
        updateSelected(null, null, null, null,
                Float.parseFloat(value.trim()));
    } catch (NumberFormatException ignored) {
        // keep last valid scale until the text parses again
    }
}
```

**Почему проблема:**  
Парсится `Float.parseFloat()` без проверки диапазона → можно ввести `scale=0` или `scale=-5` → **невидимая или инвертированная косметика**. В `CosmeticsLayer.applyTransform():112` есть guard `scale <= 0f ? 1f`, но в `EntityCrownLayer.renderItems():111` — **НЕТ**.

**Последствия:**  
- На entity-форме отрицательный scale → **инвертированная геометрия**
- scale=0 → невидимая косметика, которую нельзя найти и удалить

**Исправление:**
```java
case "scale" -> {
    try {
        float parsed = Float.parseFloat(value.trim());
        if (parsed > 0.01f && parsed < 100f) {  // разумные границы
            updateSelected(null, null, null, null, parsed);
        }
    } catch (NumberFormatException ignored) {
    }
}
```

---

## 🔵 МЕЛКИЕ УЛУЧШЕНИЯ

### 🟢 МЕЛКИЙ #1: Неиспользуемое поле loggedPIP в CosmeticsLayer (CosmeticsLayer.java:49)
**Файл:** `CosmeticsLayer.java:49-75`  
**Проблема:**
```java
private boolean loggedPIP;
// ...
if (!loggedPIP) {
    loggedPIP = true;
    dev.originsx.looks.LooksMod.LOGGER.info(...);
}
```

**Почему мелкое:**  
Одноразовый лог — **дебаг-артефакт**, забытый после разработки. Засоряет код.

**Исправление:**  
Удалить поле и логи, либо перенести на debug-уровень.

---

### 🟢 МЕЛКИЙ #2: Дублирование кода в RegistryPicker.entry() (RegistryPicker.java:151-172)
**Файл:** `RegistryPicker.java:151-172`  
**Проблема:**  
Три перегрузки `entry()` с дублирующейся логикой построения `Entry`.

**Исправление:**  
Вынести общую логику.

---

### 🟢 МЕЛКИЙ #3: Hardcoded magic numbers в LooksScreen (LooksScreen.java:69-85)
**Файл:** `LooksScreen.java:69-85`  
**Проблема:**
```java
private static final int PANEL_BG = 0xFF22222A;
private static final int ROW_BG = 0xFF2E2E38;
private static final int ROW_SELECTED = 0xFF7A5A20;
private static final int LINE_LOCKED = 0xFF666666;
```

**Почему мелкое:**  
Цвета захардкожены, нет темы → невозможно кастомизировать палитру.

**Исправление:**  
Вынести в config или использовать ldlib2-темы.

---

### 🟢 МЕЛКИЙ #4: Отсутствие cleanup в LooksScreen.closeWithoutSaving() (LooksScreen.java:961)
**Файл:** `LooksScreen.java:961-964`  
**Проблема:**
```java
private void closeWithoutSaving() {
    LooksClient.clearPreviewFor(raceId);
    Minecraft.getInstance().setScreenAndShow(null);
}
```

**Почему мелкое:**  
При закрытии экрана `previewEntity` не удаляется (если был создан в viewport) → **утечка detached-сущностей**.

**Исправление:**
```java
private void closeWithoutSaving() {
    if (previewEntity != null && !previewEntity.isRemoved()) {
        previewEntity.discard();
        previewEntity = null;
    }
    LooksClient.clearPreviewFor(raceId);
    Minecraft.getInstance().setScreenAndShow(null);
}
```

---

### 🟢 МЕЛКИЙ #5: Отсутствие проверки на пустой uuid в PlayerRaceClient.apply() (PlayerRaceClient.java:18-28)
**Файл:** `PlayerRaceClient.java:18-28`  
**Проблема:**
```java
public static void apply(String uuid, String raceId) {
    try {
        if (raceId == null || raceId.isEmpty()) {
            RACES.remove(UUID.fromString(uuid));
        } else {
            RACES.put(UUID.fromString(uuid), raceId);
        }
    } catch (IllegalArgumentException ignored) {
    }
}
```

**Почему мелкое:**  
Если `uuid` пустой или null, `UUID.fromString()` бросит `IllegalArgumentException` → тихо проглатывается, но **ненужный exception на каждый пустой пакет**.

**Исправление:**
```java
public static void apply(String uuid, String raceId) {
    if (uuid == null || uuid.isEmpty()) {
        return;
    }
    try {
        UUID id = UUID.fromString(uuid);
        if (raceId == null || raceId.isEmpty()) {
            RACES.remove(id);
        } else {
            RACES.put(id, raceId);
        }
    } catch (IllegalArgumentException ignored) {
    }
}
```

---

### 🟢 МЕЛКИЙ #6: Неоптимальная проверка в EntityForm.formOf() (EntityForm.java:138)
**Файл:** `EntityForm.java:124-139`  
**Проблема:**
```java
var holder = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(form);
return holder != null && holder.isPresent() ? form : null;
```

**Почему мелкое:**  
`BuiltInRegistries.get()` уже возвращает `Optional.empty()` для несуществующих ключей. Проверка `holder != null` избыточна.

**Исправление:**
```java
var holder = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(form);
return holder.isPresent() ? form : null;
```

---

### 🟢 МЕЛКИЙ #7: Verbose frame counter в ViewportTexture (LooksScreen.java:487-514)
**Файл:** `LooksScreen.java:487-514`  
**Проблема:**  
Диагностические логи каждые 300 и 120 фреймов → **спам в консоли** после финальной отладки.

**Исправление:**  
Удалить или перенести на debug с флагом `-Dlooks.viewportDebug=true`.

---

## 📊 СТАТИСТИКА ПРОБЛЕМ

| Категория | Количество | Приоритет |
|-----------|------------|-----------|
| 🔴 Критические | 3 | **НЕМЕДЛЕННО** |
| 🟡 Средние | 5 | Высокий |
| 🟢 Мелкие | 7 | Низкий |
| **ИТОГО** | **15** | |

---

## 🎯 РЕКОМЕНДАЦИИ ПО ИСПРАВЛЕНИЮ

### Приоритет 1 (НЕМЕДЛЕННО):
1. ✅ Убрать `static` из `EntityAimMixin.looks$aimFromMobHead()`
2. ✅ Заменить `WeakHashMap` на `HashSet` в `EntityForm.crownedTypes`
3. ✅ Добавить guard в `EntityForm.update()` перед `syncGhost()`

### Приоритет 2 (В течение недели):
4. Добавить null-check для `getParentModel()` в `CosmeticsLayer`
5. Убрать/throttle verbose logging в `CameraMixin` и `LooksScreen`
6. Увеличить порог дистанции в `EntityCrownLayer` до 0.25f
7. Добавить instanceof-check для `RenderLayerParent` в `registerCrownLayer()`
8. Валидировать диапазон scale в `LooksScreen.applyNumberField()`

### Приоритет 3 (Когда будет время):
9. Очистить debug-артефакты (loggedPIP, frameCount)
10. Cleanup previewEntity при закрытии экрана
11. Проверить uuid на пустоту в `PlayerRaceClient.apply()`
12. Убрать избыточные null-checks

---

## 🧪 ТЕСТЫ ДЛЯ ПРОВЕРКИ БАГОВ

### Для КРИТИЧЕСКИЙ #1 (EntityAimMixin):
1. Выбрать расу с entity-формой (например, axolotl)
2. Войти в игру, навести прицел на блок на уровне глаз аксолотля
3. **БАГ:** прицел попадает ВЫШЕ реальной позиции головы
4. **После фикса:** прицел точно на голове

### Для КРИТИЧЕСКИЙ #2 (WeakHashMap):
1. Переключаться между расами с разными entity-формами 100+ раз
2. Проверить `jvisualvm` → heap растёт, `crownedTypes` накапливает типы
3. **После фикса:** размер сета стабилен

### Для КРИТИЧЕСКИЙ #3 (race condition):
1. Быстро переключаться между расами (Ctrl+R → другая раса → Ctrl+R)
2. **БАГ:** случайные NPE в консоли, ghost на 1 фрейм в (0,0,0)
3. **После фикса:** плавное переключение без ошибок

---

## ✅ ЧТО УЖЕ ХОРОШО

1. ✅ Архитектура модуля чистая: разделение client/server/net
2. ✅ Миксины минимальны и целевые (только 2)
3. ✅ Косметика парсится gracefully (плохие энтри пропускаются)
4. ✅ Live-preview работает корректно (override-система)
5. ✅ Networking использует современный NeoForge API
6. ✅ UI использует ldlib2 (Taffy layout)
7. ✅ Поддержка локализации (en/ru/uk)
8. ✅ Entity-форма правильно синкается с игроком

---

## 🔧 ИНСТРУМЕНТЫ ДЛЯ ДАЛЬНЕЙШЕЙ ПРОВЕРКИ

1. **Compile-time:**
   ```bash
   ./gradlew :Looks:compileJava --warning-mode all
   ```

2. **Runtime profiling:**
   - JVisualVM для проверки утечек памяти
   - F3+L для reload-циклов (проверка очистки кэшей)

3. **Mixin debug:**
   ```
   -Dmixin.debug.verbose=true -Dmixin.debug.export=true
   ```

---

**КОНЕЦ АУДИТА**

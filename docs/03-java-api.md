# 03 · Java API — guide for modders

The Race API lets other mods add races and powers in code. Two styles are supported:

1. **Annotated classes** (`@AutoRace` / `@AutoPower`) — zero-boilerplate registration.
2. **Manual registration** via `RaceRegistry` / `PowerRegistry` — full control.

Add the API to your `build.gradle` (local jar) or depend on it via your modding setup:

```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')  // the "Race API-26.2-x.jar"
}
```

---

## Races

### Option A — annotated class

```java
package com.example.mymod;

import dev.raceapi.api.AutoRace;
import dev.raceapi.race.Race;
import dev.raceapi.race.Power;
import dev.raceapi.power.FlightPower;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@AutoRace("mymod:avian")
public class Avian implements Race {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mymod", "avian");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("race.mymod.avian.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("race.mymod.avian.desc");
    }

    @Override
    public List<Power> getPowers() {
        return List.of(new FlightPower(ID.withSuffix("_flight"), 2));
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.FEATHER);
    }

    @Override
    public int getDifficulty() {
        return 3;
    }

    @Override
    public float getScale() {
        return 0.9f;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public void onSelect(ServerPlayer player) {
        // called when the race is granted
    }

    @Override
    public void onRemove(ServerPlayer player) {
        // called when the race is removed
    }
}
```

### Option B — `SimpleRace` (chainable, less code)

```java
import dev.raceapi.race.SimpleRace;
import dev.raceapi.race.RaceRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

SimpleRace avian = new SimpleRace(ResourceLocation.fromNamespaceAndPath("mymod", "avian"))
        .displayName(Component.translatable("race.mymod.avian.name"))
        .description(Component.translatable("race.mymod.avian.desc"))
        .icon(new ItemStack(Items.FEATHER))
        .difficulty(3)
        .scale(0.9f)
        .powers(new FlightPower(ResourceLocation.fromNamespaceAndPath("raceapi", "creative_flight"), 2),
                new SafeLandingPower(ResourceLocation.fromNamespaceAndPath("raceapi", "safe_landing"), 2));
RaceRegistry.register(avian);
```

### Registering

From your mod's constructor (or an `@EventBusSubscriber` MOD-bus init):

```java
import dev.raceapi.api.RaceApi;

// annotated classes
RaceApi.registerRaces(Avian.class, Aquatic.class);

// or manually
RaceRegistry.register(avian);
```

`@AutoRace` requires a **public no-arg constructor**.

---

## Powers

### `Power` interface

```java
public interface Power {
    ResourceLocation getId();
    Component getDisplayName();
    Component getDescription();

    default int getDifficulty() { return 1; }   // negative = weakness (red in GUI)
    default boolean isHidden() { return false; }
    default boolean hasBinding() { return false; }  // true = triggered by the active-power key

    default void onAttach(ServerPlayer player) {}
    default void onRemove(ServerPlayer player) {}
    default void onTick(ServerPlayer player) {}          // every server tick
    default void onKeyPressed(ServerPlayer player) {}    // active ability
    default void onJump(ServerPlayer player) {}
    default float onFall(ServerPlayer player, float fallDistance) { return fallDistance; }
    default void onBreakBlock(ServerPlayer player, BlockPos pos, BlockState state) {}
    default float onHurt(ServerPlayer player, DamageSource source, float amount) { return amount; }
}
```

All hooks run on the **server thread**.

### Annotated power

```java
import dev.raceapi.api.AutoPower;
import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

@AutoPower("mymod:gale_dash")
public class GaleDash implements Power {

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath("mymod", "gale_dash");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.mymod.gale_dash.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.mymod.gale_dash.desc");
    }

    @Override
    public boolean hasBinding() {
        return true;
    }

    @Override
    public void onKeyPressed(ServerPlayer player) {
        player.setDeltaMovement(player.getDeltaMovement().add(
                player.getLookAngle().x * 1.8, 0.25, player.getLookAngle().z * 1.8));
        player.hurtMarked = true;
    }
}
```

Register:

```java
RaceApi.registerPowers(GaleDash.class);
```

### Manual power registration

```java
PowerRegistry.register(
        ResourceLocation.fromNamespaceAndPath("mymod", "gale_dash"),
        () -> new GaleDash());
```

### Reusing built-in powers in Java

The built-in ids (`raceapi:dash`, `raceapi:blink`, ...) can be pulled via `PowerRegistry.createOrNull(id)`:

```java
Power dash = PowerRegistry.createOrNull(ResourceLocation.fromNamespaceAndPath("raceapi", "dash"));
```

---

## Localization

The GUI resolves names through the same lang system as Minecraft. Add a lang file to your mod:

```json
{
  "race.mymod.avian.name": "Avian",
  "race.mymod.avian.desc": "Light as a feather.",
  "power.mymod.gale_dash.name": "Gale Dash",
  "power.mymod.gale_dash.desc": "Launch yourself forward."
}
```

---

## Ordering & overriding

- **Java races always beat JSON races** with the same id.
- **Java powers always beat JSON powers** with the same id.
- JSON races are wiped and re-read on every `/reload`; Java ones are registered once at mod load.

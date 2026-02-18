# AreaEffect

Zone-based potion/gameplay effect application DSL. Applies effects to players inside a defined region, with enter/exit callbacks.

## Usage

```kotlin
val zone = areaEffect("speed-zone") {
    region(cuboidRegion("arena", Pos(0.0, 60.0, 0.0), Pos(50.0, 80.0, 50.0)))
    instance(myInstance)
    effect(PotionEffect.SPEED, 1, 100)
    effect(PotionEffect.REGENERATION, 0, 60)
    interval(20)
    onEnter { player -> player.sendMM("<green>You entered the speed zone!") }
    onExit { player -> player.sendMM("<red>You left the speed zone.") }
}

AreaEffectManager.register(zone)
```

## API

- `areaEffect(name) { }` -- DSL to create an `AreaEffectZone`
- `AreaEffectZone.start()` / `stop()` -- start/stop timer-based scanning
- `AreaEffectZone.isInside(player)` -- check if player is in zone
- `AreaEffectManager.register(zone)` -- register and auto-start
- `AreaEffectManager.unregister(name)` -- stop and remove
- `AreaEffectManager.zonesAt(player)` -- all zones containing player
- `AreaEffectManager.stopAll()` -- stop and clear all zones

## Behavior

- Timer scans every `intervalTicks` for players inside the region
- Effects are re-applied each tick to maintain duration
- Effects are automatically removed when player exits the region
- Enter/exit callbacks fire on first entry and on leaving

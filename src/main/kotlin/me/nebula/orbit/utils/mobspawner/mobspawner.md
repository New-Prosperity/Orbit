# MobSpawner

Advanced mob spawning DSL with AI, equipment, drops, and wave spawning.

## Single Mob

```kotlin
val mob = spawnMob(EntityType.ZOMBIE) {
    instance(inst)
    position(Pos(0.0, 64.0, 0.0))
    health(40f)
    speed(0.3)
    attackDamage(6f)
    hostile()
    customName("<red>Boss Zombie")
    drops {
        item(Material.ROTTEN_FLESH, chance = 0.5)
        item(Material.IRON_INGOT, amount = 2, chance = 0.1)
    }
    equipment {
        helmet(Material.DIAMOND_HELMET)
        chestplate(Material.IRON_CHESTPLATE)
    }
    onDeath { killer -> killer?.sendMM("<green>You killed the boss!") }
    onSpawn { creature -> }
}
```

## Wave Spawner

```kotlin
val waves = waveSpawner {
    instance(inst)
    position(Pos(0.0, 64.0, 0.0))
    delay(Duration.ofSeconds(10))
    wave(1) { mob(EntityType.ZOMBIE, count = 5) }
    wave(2) { mob(EntityType.SKELETON, count = 3) }
    wave(3) {
        mob(EntityType.ZOMBIE, count = 8) { health(30f) }
        mob(EntityType.SKELETON, count = 4)
    }
    onWaveStart { wave -> }
    onWaveEnd { wave -> }
    onAllWavesComplete { }
}
waves.start()
waves.stop()
```

## Spawner Point

```kotlin
val point = MobSpawnerPoint(
    entityType = EntityType.ZOMBIE,
    instance = inst,
    position = Pos(0.0, 64.0, 0.0),
    interval = Duration.ofSeconds(30),
    maxAlive = 5,
) { health(20f); hostile() }

point.start()
point.stop()
point.aliveCount
```

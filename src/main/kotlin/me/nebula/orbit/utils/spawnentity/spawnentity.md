# SpawnEntity

DSL builder for spawning and configuring entities in an instance.

## Usage

```kotlin
val zombie = spawnEntity(EntityType.ZOMBIE) {
    instance(myInstance)
    position(Pos(0.0, 64.0, 0.0))
    customName("<red>Boss Zombie")
    glowing()
    health(40f)
    velocity(Vec(0.0, 10.0, 0.0))
    onSpawn { entity -> println("Spawned ${entity.entityId}") }
}

instance.spawnEntity(EntityType.ARMOR_STAND) {
    position(10.0, 65.0, 10.0)
    invisible()
    noGravity()
    customName("<gold>Hologram")
}
```

## Key API

- `spawnEntity(entityType) { }` -- DSL to build and spawn an entity
- `Instance.spawnEntity(entityType) { }` -- same, with instance pre-set
- `position(pos)` / `position(x, y, z)` -- spawn location
- `instance(inst)` -- target instance (required unless using the extension)
- `velocity(vec)` / `velocity(x, y, z)` -- initial velocity
- `customName(text)` -- MiniMessage display name (auto-shows)
- `noGravity()` / `silent()` / `invisible()` / `glowing()` -- entity flags
- `health(hp)` -- set health (LivingEntity only)
- `onSpawn { }` -- callback after entity is placed in the world

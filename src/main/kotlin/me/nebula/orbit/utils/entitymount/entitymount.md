# EntityMount

Entity mounting/riding system with stacking, speed multipliers, and player extensions.

## Manager API

```kotlin
EntityMountManager.mount(rider, vehicle)
EntityMountManager.mount(rider, vehicle, MountConfig(speedMultiplier = 2.0))
EntityMountManager.dismount(rider)
EntityMountManager.getMountedVehicle(rider)
EntityMountManager.getRiders(vehicle)
EntityMountManager.isMounted(rider)
EntityMountManager.getMountConfig(rider)
EntityMountManager.dismountAll()
EntityMountManager.cleanup(uuid)
```

## Mount Stacking

```kotlin
EntityMountManager.mountStack(listOf(entityA, entityB, entityC))
```

## Config DSL

```kotlin
val config = mountConfig {
    speedMultiplier(1.5)
    jumpBoost(0.5)
    steeringOverride(true)
}
EntityMountManager.mount(player, horse, config)
```

## Player Extensions

```kotlin
player.mountEntity(entity)
player.mountEntity(entity, config)
player.dismountEntity()
player.getMountedEntity()
```

## Behavior

- Mounting automatically dismounts from any previous vehicle.
- `mountStack(entities)` chains riders bottom-to-top: A carries B carries C.
- `speedMultiplier` modifies the vehicle's `MOVEMENT_SPEED` attribute.
- `cleanup(uuid)` for disconnect cleanup.

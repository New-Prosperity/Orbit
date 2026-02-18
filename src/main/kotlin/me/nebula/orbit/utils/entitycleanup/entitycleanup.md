# EntityCleanup

Automatic per-instance entity cleanup system with age limits, capacity caps, and warnings.

## DSL

```kotlin
entityCleanup(instance) {
    maxAge(Duration.ofMinutes(5))
    maxPerInstance(200)
    excludeTypes(EntityType.PLAYER, EntityType.ARMOR_STAND)
    warningAt(180)
    warningMessage("<yellow>Cleaning entities in {time}s")
    cleanupInterval(Duration.ofSeconds(30))
}
```

## Manager API

```kotlin
EntityCleanupManager.install(instance, config)
EntityCleanupManager.uninstall(instance)
EntityCleanupManager.forceCleanup(instance)
EntityCleanupManager.uninstallAll()
```

## Behavior

- Runs periodic cleanup on a configurable interval.
- Removes entities older than `maxAge` first, then enforces `maxPerInstance` cap.
- Cleanup priority: item entities before living entities, oldest first.
- `{time}` and `{count}` placeholders available in warning template.
- Players are always excluded from cleanup regardless of config.

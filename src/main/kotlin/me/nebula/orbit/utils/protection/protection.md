# Protection

Unified zone-based protection system. Supports region, chunk, and radius zones with per-flag control, whitelists, priority, and a single shared EventNode.

## ProtectionFlag

| Flag | Blocks |
|---|---|
| `BREAK` | Block breaking |
| `PLACE` | Block placing |
| `INTERACT` | Block interaction |
| `PVP` | Player-vs-player damage |
| `MOB_DAMAGE` | Mob damage |

## ProtectionZone (sealed interface)

All zones implement `isProtected(point, player): Boolean` and expose `flags: Set<ProtectionFlag>`.

| Zone | Key Fields |
|---|---|
| `RegionZone` | `name`, `region: Region`, `flags`, `whitelist: Set<UUID>`, `priority: Int` |
| `ChunkZone` | `instance`, `chunkX`, `chunkZ`, `flags`, `whitelist: Set<UUID>` |
| `RadiusZone` | `name`, `center: Point`, `radius: Double`, `instance`, `flags`, `bypass: (Player) -> Boolean` |

## ProtectionManager

| Method | Description |
|---|---|
| `install()` | Registers break/place/interact listeners on a global EventNode |
| `uninstall()` | Removes EventNode and clears all zones |
| `protect(key, zone)` | Register a zone by key |
| `unprotect(key)` | Remove a zone by key |
| `isBlocked(player, point, flag)` | Check if action is blocked (priority-sorted) |
| `all()` | Snapshot of all registered zones |
| `clearInstance(instance)` | Remove all ChunkZone/RadiusZone tied to an instance |
| `clear()` | Remove all zones |

## DSL Functions

### protectRegion

```kotlin
protectRegion("spawn") {
    region(CuboidRegion(Pos(-50.0, 0.0, -50.0), Pos(50.0, 256.0, 50.0)))
    flags(ProtectionFlag.BREAK, ProtectionFlag.PLACE, ProtectionFlag.INTERACT)
    whitelist(adminUuid)
    priority(10)
}
```

### protectChunk

```kotlin
protectChunk(instance) {
    chunk(0, 0) {
        flags(ProtectionFlag.BREAK, ProtectionFlag.PLACE)
    }
    area(-2, -2, 2, 2) {
        flags(ProtectionFlag.BREAK)
        whitelist(builderUuid)
    }
}
```

### protectSpawn

```kotlin
protectSpawn("hub-spawn", center = Pos(0.0, 64.0, 0.0), radius = 30.0, instance = instance) {
    flags(ProtectionFlag.BREAK, ProtectionFlag.PLACE, ProtectionFlag.PVP)
    bypass { it.permissionLevel >= 2 }
}

protectSpawn("lobby", center = spawnPoint, radius = 50.0, instance = instance)
```

Default flags for `protectSpawn` are `BREAK` and `PLACE`. Use `allowBreak()` / `allowPlace()` to remove defaults, or `flags(...)` to replace entirely.

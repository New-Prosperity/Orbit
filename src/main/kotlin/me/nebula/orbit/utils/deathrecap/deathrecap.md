# DeathRecap

Damage tracking and death recap utility. Records all damage dealt to players and generates a summary on elimination showing killer info (weapon, distance, health), survival time, damage breakdown with per-entry weapon/distance, and assists. Enabled by default in all game modes via `GameMode.buildDeathRecapTracker()`.

## API

### Tracking

```kotlin
val recapTracker = DeathRecapTracker()
recapTracker.gameStartTime = System.currentTimeMillis()

recapTracker.recordDamage(victim.uuid, DamageEntry(
    attackerUuid = attacker.uuid,
    attackerName = attacker.username,
    amount = damage,
    source = "PLAYER",
    weapon = attacker.itemInMainHand.material(),
    distance = attacker.position.distance(victim.position),
))
```

### Sending Recap

```kotlin
recapTracker.sendRecap(eliminatedPlayer)
```

Sends a formatted chat message to the eliminated player showing:
- Who killed them, with what weapon, from what distance, and their remaining health
- How long the player survived since game start
- Total damage taken in the last 30 seconds
- Last 5 damage sources with attacker names, amounts, weapons, and distances
- Assists (other players who dealt damage)

Stores the formatted recap lines for later retrieval via `/lastdeath`.

### DeathRecap Data

```kotlin
val recap = recapTracker.buildRecap(player)
recap?.killerName        // Name of the killing blow dealer
recap?.killerHealth      // Killer's health at time of query
recap?.killerWeapon      // Material the killer was holding
recap?.killerDistance     // Distance between killer and victim
recap?.totalDamage       // Total damage in window
recap?.survivalTimeMs    // Milliseconds since gameStartTime
recap?.assists           // List of AssistInfo(uuid, name, damage)
recap?.entries           // Raw DamageEntry list (with weapon, distance per entry)
```

### Last Recap Retrieval

```kotlin
recapTracker.getLastRecap(uuid)  // Returns List<Component>? of the last sent recap
```

### Cleanup

```kotlin
recapTracker.clearPlayer(uuid)  // Clears damage history and stored recap
recapTracker.clear()            // Clears all data
```

### Integration

`GameMode.buildDeathRecapTracker()` returns `DeathRecapTracker()` by default, enabling recaps for all game modes. Override to return `null` to disable. The base `GameMode` records damage (with weapon and distance) in its `EntityDamageEvent` listener and calls `sendRecap()` in `handleDeath()`. The `/lastdeath` command re-displays the last stored recap.

### Translation Keys

- `orbit.deathrecap.header` — recap header bar
- `orbit.deathrecap.killer` — killer line without weapon/distance
- `orbit.deathrecap.killer_with_weapon` — killer line with weapon, distance, health
- `orbit.deathrecap.survived` — survival time line
- `orbit.deathrecap.total_damage` — total damage line
- `orbit.deathrecap.entry_detailed` — per-entry with weapon and distance
- `orbit.deathrecap.entry_environment` — per-entry for environment damage (no weapon/distance)
- `orbit.deathrecap.assists` — assists line
- `orbit.deathrecap.no_recap` — no recap available message

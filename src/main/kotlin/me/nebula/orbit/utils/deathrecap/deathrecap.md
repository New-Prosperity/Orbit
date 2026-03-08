# DeathRecap

Damage tracking and death recap utility. Records all damage dealt to players and generates a summary on elimination showing killer health, damage breakdown, and assists.

## API

### Tracking

```kotlin
val recapTracker = DeathRecapTracker()

recapTracker.recordDamage(victim.uuid, DamageEntry(
    attackerUuid = attacker.uuid,
    attackerName = attacker.username,
    amount = damage,
    source = "MELEE",
))
```

### Sending Recap

```kotlin
recapTracker.sendRecap(eliminatedPlayer)
```

Sends a formatted chat message to the eliminated player showing:
- Who killed them and their remaining health
- Total damage taken in the last 30 seconds
- Last 5 damage sources with attacker names and amounts
- Assists (other players who dealt damage)

### DeathRecap Data

```kotlin
val recap = recapTracker.buildRecap(player)
recap?.killerName        // Name of the killing blow dealer
recap?.killerHealth      // Killer's health at time of query
recap?.totalDamage       // Total damage in window
recap?.assists           // List of AssistInfo(uuid, name, damage)
recap?.entries           // Raw DamageEntry list
```

### Cleanup

```kotlin
recapTracker.clearPlayer(uuid)
recapTracker.clear()
```

### Integration

Game modes create a `DeathRecapTracker`, record damage in their `EntityDamageEvent` handler, and call `sendRecap()` in `onPlayerEliminated()`.

# AntiCheat

Lightweight server-side anti-cheat module for Orbit. Detects only blatant cheating with generous thresholds to minimize false positives.

## Usage

```kotlin
val handler = MinecraftServer.getGlobalEventHandler()
AntiCheat.install(handler)
```

To uninstall:
```kotlin
AntiCheat.uninstall()
```

Notify teleport to suppress false flags after server-initiated movement:
```kotlin
MovementCheck.notifyTeleport(player.uuid)
```

## Checks

### Movement (`MovementCheck`)
| Check | Threshold | Description |
|-------|-----------|-------------|
| Fly | Y > 0.5 blocks/tick while airborne 3+ ticks | Detects upward flight in survival/adventure |
| Speed | Horizontal > 0.65 blocks/tick | Catches speed hacks (sprint-jump max is ~0.6) |
| NoFall | Fall > 4 blocks with instant ground flag | Detects ground-spoof packets |

- Weight: 1 per violation
- Kick threshold: 20 violations in 30s window
- Skips: creative/spectator mode, players in vehicles, 20 ticks after teleport

### Combat (`CombatCheck`)
| Check | Threshold | Description |
|-------|-----------|-------------|
| Reach | Distance > 4.5 blocks | Vanilla reach is 3.0, generous margin for lag |
| Attack Rate | > 20 CPS | Vanilla max is ~16 with perfect timing |

- Weight: 2 per violation
- Kick threshold: 15 violations in 30s window
- Skips: creative mode

## Bypass

Players with `orbit.anticheat.bypass` permission (checked via `RankManager`) are exempt from all checks.

## Cleanup

Player state is automatically cleaned up on `PlayerDisconnectEvent`. `ViolationTracker` uses a 30-second sliding window; old violations decay automatically.

# RandomTeleport

Random teleport within bounds with safe location finding.

## Usage

```kotlin
val result = randomTeleport(player) {
    minX(-500)
    maxX(500)
    minZ(-500)
    maxZ(500)
    instance(myInstance)
    maxAttempts(10)
    safeCheck(true)
}

if (result.success) {
    player.sendMM("<green>Teleported after ${result.attempts} attempts!")
}
```

Extension form:

```kotlin
player.randomTeleport {
    minX(-1000)
    maxX(1000)
    minZ(-1000)
    maxZ(1000)
    maxAttempts(20)
}
```

## API

- `randomTeleport(player) { }` -- DSL to configure and execute random teleport
- `Player.randomTeleport { }` -- extension function form
- `RandomTeleportResult` -- contains `success`, `position`, and `attempts`

## Safe Check

When `safeCheck(true)` (default):
- Scans top-down from `maxY` to `minY`
- Requires: solid non-liquid block below, 2 air blocks at location
- Returns failure if no safe position found within `maxAttempts`

When `safeCheck(false)`:
- Teleports to `maxY` at random X/Z coordinates immediately

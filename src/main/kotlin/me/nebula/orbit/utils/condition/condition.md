# Condition

Composable conditions for game logic. Functional `Condition<Player>` interface with combinators.

## Usage

```kotlin
val canJoin = hasPermission("game.join") and isAlive() and not(isSneaking())

if (player.meets(canJoin)) { ... }
```

## Combinators

```kotlin
val a = isAlive()
val b = hasHealth(10f)

a and b       // both must pass
a or b        // either passes
a xor b       // exactly one passes
!a            // negation
not(a)        // negation (function form)
allOf(a, b)   // all must pass
anyOf(a, b)   // any passes
noneOf(a, b)  // none pass
```

## Built-in Conditions

- `isAlive()` - Player is not dead
- `hasHealth(min)` - Player health >= min
- `hasItem(material)` - Inventory contains material
- `hasPermission(perm)` - Player has orbit permission
- `isInRegion(region)` - Player position in Region
- `isOnGround()` - Player on ground
- `isSneaking()` - Player sneaking
- `isSprinting()` - Player sprinting
- `hasMinPlayers(count)` - Instance has >= count players
- `isInInstance(instance)` - Player in specific instance
- `hasExperience(min)` - Player level >= min

## Custom Conditions

```kotlin
val isNearSpawn = condition { player ->
    player.position.distance(Pos(0.0, 64.0, 0.0)) < 50.0
}
```

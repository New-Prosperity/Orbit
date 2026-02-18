# VelocityHelper

Velocity calculation helpers for common movement patterns. All functions set entity velocity directly using Minestom's ticks-per-second scaling.

## Usage

```kotlin
player.launchUp(1.5)
player.launchForward(2.0)
player.launchToward(targetPos, 1.0)
player.knockbackFrom(explosionPos, 1.5)
entity.freeze()

val arcVelocity = calculateParabolicVelocity(from, to, heightFactor = 3.0)
entity.velocity = arcVelocity
```

## API

- `Player.launchUp(power)` -- vertical launch, preserves horizontal velocity
- `Player.launchForward(power)` -- launch in player's look direction (yaw + pitch)
- `Player.launchToward(target, power)` -- launch toward a specific point
- `Player.knockbackFrom(source, power)` -- knockback away from a point (horizontal + upward)
- `Entity.freeze()` -- zero velocity
- `calculateParabolicVelocity(from, to, heightFactor): Vec` -- calculate arc trajectory velocity between two points

## Notes

- All power values are multiplied by 20 (ticks per second) for proper Minestom velocity scaling
- `knockbackFrom` applies half power vertically for natural knockback feel
- `calculateParabolicVelocity` computes based on gravity (0.08) and the desired peak height above the higher point

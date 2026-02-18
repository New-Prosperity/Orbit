# EntityFormation

Entity formation system for arranging entities in geometric patterns with optional animation.

## Key Classes

- **`FormationPattern`** -- sealed interface with Circle, Line, Grid, Wedge variants
- **`Formation`** -- computed formation that can be applied or animated
- **`FormationBuilder`** -- DSL builder

## Usage

```kotlin
val formation = entityFormation {
    circle(radius = 5.0, count = 8)
    yawOffset(45f)
}

formation.apply(entities, center = Pos(0.0, 65.0, 0.0))

val task = formation.animate(entities, center, speed = 2.0)
formation.stopAnimation()
```

## Patterns

```kotlin
entityFormation { circle(radius = 3.0, count = 6) }
entityFormation { line(spacing = 2.0, direction = Vec(1.0, 0.0, 0.0)) }
entityFormation { grid(rows = 3, cols = 4, spacing = 2.0) }
entityFormation { wedge(angle = 60.0, spacing = 2.0) }
```

## Pattern Details

| Pattern | Behavior |
|---------|----------|
| Circle | Evenly distributed around circumference at given radius |
| Line | Centered along direction vector with given spacing |
| Grid | Rows x cols grid, centered on origin, rotated by yawOffset |
| Wedge | V-shape spread at given angle, distance increases per rank |

## Animation

`animate()` rotates formation around center every tick by `speed` degrees. Returns a `Task` handle.

```kotlin
val task = formation.animate(entities, center, speed = 1.5)
formation.stopAnimation()
```

## Details

- `apply()` teleports entities to computed positions
- `animate()` runs a repeating tick task that rotates positions
- Only entities within position count are moved (extras ignored)
- Removed entities are skipped during animation
- Grid pattern supports yaw rotation
- Circle uses evenly spaced angles with optional yaw offset
- Line centers entities along direction vector

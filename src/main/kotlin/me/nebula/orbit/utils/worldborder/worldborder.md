# World Border

DSL for creating managed world borders with animated shrink/expand transitions.

## Key Classes

- **`ManagedWorldBorder`** -- wraps Minestom's `WorldBorder` with stateful management
- **`WorldBorderBuilder`** -- DSL builder

## Usage

### Create

```kotlin
val border = instance.managedWorldBorder {
    diameter(500.0)
    center(0.0, 0.0)
    warningDistance(10)
    warningTime(15)
}
```

### Modify

```kotlin
border.shrinkTo(100.0, transitionSeconds = 60.0)

border.expandTo(500.0, transitionSeconds = 30.0)

border.setDiameter(200.0)
border.setCenter(50.0, 50.0)
```

### Queries

```kotlin
border.currentDiameter
border.isOutside(pos)
border.isInside(pos)
```

## API

| Method | Description |
|--------|-------------|
| `apply()` | Re-apply current border to instance |
| `setDiameter(d)` | Instant diameter change |
| `setCenter(x, z)` | Move border center |
| `shrinkTo(d, seconds)` | Animated shrink over time |
| `expandTo(d, seconds)` | Animated expand over time |
| `isOutside(pos)` | Check if position is outside border |
| `isInside(pos)` | Check if position is inside border |

The DSL auto-applies the border to the instance on creation.

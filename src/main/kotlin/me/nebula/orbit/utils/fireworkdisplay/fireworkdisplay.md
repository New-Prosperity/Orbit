# Firework Display

Programmatic firework show builder DSL. Schedule timed firework launches with configurable colors, shapes, and effects.

## FireworkShape

`BALL`, `BALL_LARGE`, `STAR`, `BURST`, `CREEPER`

## DSL

```kotlin
val show = fireworkShow(instance) {
    at(0) {
        launch(Pos(0.0, 64.0, 0.0)) {
            color(NamedTextColor.RED)
            color(255, 200, 0)
            shape(FireworkShape.BALL_LARGE)
            trail()
            flicker()
            flightTicks(40)
        }
    }
    at(20) {
        launch(Pos(5.0, 64.0, 0.0)) {
            color(NamedTextColor.BLUE)
            shape(FireworkShape.STAR)
        }
        launch(Pos(-5.0, 64.0, 0.0)) {
            color(NamedTextColor.GREEN)
            shape(FireworkShape.BURST)
        }
    }
}

show.start()
show.cancel()
```

## Quick Launch

```kotlin
instance.launchFirework(
    position = Pos(0.0, 64.0, 0.0),
    velocity = Vec(0.0, 20.0, 0.0),
    flightTicks = 30,
)
```

## Notes

- `at(tickOffset)` schedules launches relative to `show.start()` time
- Multiple launches per `at()` block fire simultaneously
- `cancel()` stops all pending launches and removes active firework entities

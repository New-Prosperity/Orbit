# Sound

DSL for creating reusable sound effects and player extension functions for quick playback.

## Key Classes

- **`SoundEffect`** — immutable sound config with `play` overloads
- **`SoundBuilder`** — DSL builder

## Usage

```kotlin
val click = soundEffect(SoundEvent.UI_BUTTON_CLICK) {
    volume = 0.8f
    pitch = 1.2f
    source = Sound.Source.MASTER
}

click.play(player)
click.play(player, Pos(0.0, 65.0, 0.0))
click.play(instance, Pos(0.0, 65.0, 0.0))
```

## Extension Functions

```kotlin
player.playSound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, volume = 1f, pitch = 1f)
player.playSound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Pos(0.0, 65.0, 0.0))
```

## Builder Properties

| Property | Default | Description |
|----------|---------|-------------|
| `volume` | `1f` | Playback volume |
| `pitch` | `1f` | Playback pitch |
| `source` | `MASTER` | Sound category |

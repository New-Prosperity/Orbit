# Jump Pad

Player launch pad system triggered by proximity to configured block positions. Supports fixed velocity and direction-based (player-facing) launching with per-player cooldowns.

## Key Classes

- **`JumpPad`** — immutable config (position, launch mode, sound, particle, cooldown)
- **`LaunchMode`** — sealed interface: `Fixed(velocity)` or `Forward(power, upward)`
- **`JumpPadManager`** — singleton managing all pads, event listeners, and cooldowns
- **`JumpPadBuilder`** — DSL builder

## Usage

### Fixed velocity

```kotlin
val pad = jumpPad {
    position(Pos(10.0, 64.0, 10.0))
    velocity(Vec(0.0, 1.5, 2.0))
    sound(SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH)
    cooldown(10)
}
val id = JumpPadManager.register(pad, instance)
```

### Direction-based (player facing)

```kotlin
val pad = jumpPad {
    position(Pos(10.0, 64.0, 10.0))
    launchForward(power = 2.0, upward = 1.0)
    sound(SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH)
}
JumpPadManager.register(pad, instance)
```

### Cleanup

```kotlin
JumpPadManager.unregister(id)
JumpPadManager.unregisterAll(instance)
JumpPadManager.clear()
```

## Builder Properties

| Property | Default | Description |
|---|---|---|
| `position` | `Pos.ZERO` | Trigger center position |
| `velocity` | — | Fixed launch vector (use `velocity` or `launchForward`) |
| `launchForward` | — | Direction-based launch (power + upward) |
| `sound` | `ENTITY_FIREWORK_ROCKET_LAUNCH` | Sound played on launch |
| `particle` | `FIREWORK` | Particle spawned at pad on launch |
| `cooldown` | `10` | Cooldown in ticks between launches per player |
| `triggerRadius` | `0.8` | Distance from position center to trigger |

## Launch Modes

| Mode | Behavior |
|---|---|
| `Fixed(velocity)` | Applies exact `Vec` velocity to player |
| `Forward(power, upward)` | Launches player in their facing direction with `power` horizontal + `upward` vertical |

## Manager Methods

| Method | Description |
|---|---|
| `register(pad, instance)` | Register a pad, returns ID string |
| `unregister(id)` | Remove a single pad by ID |
| `unregisterAll(instance)` | Remove all pads for an instance |
| `clear()` | Remove all pads and listeners |

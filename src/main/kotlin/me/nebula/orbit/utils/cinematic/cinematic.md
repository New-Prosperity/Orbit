# Cinematic Camera

Scripted camera sequences with keyframe-driven position interpolation (catmull-rom, bezier, linear, step) and quaternion slerp rotation.

## Usage

```kotlin
import me.nebula.orbit.utils.cinematic.*

cinematic(player) {
    node(0f, Pos(100.0, 80.0, 100.0, -45f, -20f))
    node(3f, Pos(110.0, 85.0, 110.0, 0f, -10f))
    node(6f, Pos(120.0, 80.0, 100.0, 45f, -20f))
    onComplete { player.sendMessage(Component.text("Done")) }
}
```

### With lookAt target

Rotation keyframes are ignored — camera always faces the target:

```kotlin
cinematic(player) {
    node(0f, Pos(100.0, 80.0, 100.0))
    node(5f, Pos(120.0, 85.0, 120.0))
    lookAt(targetEntity)          // dynamic: tracks entity position each tick
    // lookAt(Vec(50.0, 65.0, 50.0))  // static point
    // lookAt { someVec() }           // custom supplier
}
```

### Looping

```kotlin
cinematic(player) {
    node(0f, Pos(0.0, 80.0, 0.0))
    node(5f, Pos(50.0, 80.0, 50.0))
    loop()
}
```

### Interpolation types

Default is `CATMULLROM`. Override per-node:

```kotlin
node(0f, Pos(0.0, 80.0, 0.0), InterpolationType.LINEAR)
node(3f, Pos(50.0, 80.0, 50.0), InterpolationType.BEZIER)
```

### Extensions

```kotlin
player.playCinematic { node(...); node(...) }
player.stopCinematic()
player.isInCinematic  // Boolean
```

## API

| Function | Description |
|---|---|
| `cinematic(player) { }` | Build and play a cinematic sequence |
| `CinematicCamera.play(player, sequence)` | Play a pre-built `CinematicSequence` |
| `CinematicCamera.stop(player)` | Stop and restore gamemode |
| `CinematicCamera.isPlaying(player)` | Check if player has active cinematic |
| `CinematicCamera.stopAll()` | Stop all active sessions |
| `CinematicCamera.install(eventNode)` | Explicitly register disconnect cleanup (optional — `play()` auto-installs on first call) |

## Internals

- Fully packet-based: spawns a virtual invisible armor stand (negative entity ID, same pattern as `BoneRenderer`) via `SpawnEntityPacket` + `EntityMetaDataPacket` — no server-side entity registered
- Locks player camera via `CameraPacket(entityId)`; moves camera via `EntityTeleportPacket` each tick — client-interpolated, no teleport stutter
- Player is set to `GameMode.SPECTATOR` during playback; previous gamemode is restored on stop
- Disconnect cleanup auto-installs on the global event handler on first `play()` call (via `AtomicBoolean` guard), or can be explicitly installed via `install(eventNode)`
- On stop: resets camera via `CameraPacket(player.entityId)`, destroys virtual entity via `DestroyEntitiesPacket`
- Position: `KeyframeInterpolator` (reuses modelengine's interpolation pipeline)
- Rotation: `quatSlerp` between `eulerToQuat` keyframe quaternions (no gimbal lock)
- Tick rate: 1 tick (50ms) via `Scheduler.repeat(1)`
- Minimum 2 nodes required

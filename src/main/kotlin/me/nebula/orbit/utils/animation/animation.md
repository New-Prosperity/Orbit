# Animation

Block and entity animation systems with keyframe support, looping, and interpolation.

## Block Animation DSL

```kotlin
val anim = blockAnimation(instance) {
    intervalTicks = 5
    loop = true
    restoreOnComplete = true
    packetOnly = true

    frame {
        put(Pos(0, 64, 0), Block.REDSTONE_BLOCK)
        put(Pos(1, 64, 0), Block.AIR)
    }
    frame {
        put(Pos(0, 64, 0), Block.AIR)
        put(Pos(1, 64, 0), Block.REDSTONE_BLOCK)
    }
}
```

| Method | Description |
|---|---|
| `start()` | Begin playing frames (saves originals if `restoreOnComplete`) |
| `stop()` | Stop and restore original blocks if enabled |

## Entity Animation DSL

```kotlin
val anim = entityAnimation(entity) {
    intervalTicks = 1
    loop = true
    interpolate = true

    keyframe(Pos(0.0, 64.0, 0.0))
    keyframe(Pos(10.0, 64.0, 0.0))
    keyframe(Pos(10.0, 64.0, 10.0))
    keyframe(0.0, 64.0, 0.0)
}
```

| Method | Description |
|---|---|
| `start()` | Begin moving through keyframes |
| `stop()` | Stop the animation |

## Properties

### BlockAnimation

| Property | Default | Description |
|---|---|---|
| `intervalTicks` | `5` | Ticks between frames |
| `loop` | `false` | Loop after last frame |
| `restoreOnComplete` | `true` | Restore original blocks on stop |
| `packetOnly` | `false` | Use `BlockChangePacket` instead of `instance.setBlock()` — visual only, no collision or server state change |

### EntityAnimation

| Property | Default | Description |
|---|---|---|
| `intervalTicks` | `1` | Ticks per interpolation step |
| `loop` | `false` | Loop through keyframes |
| `interpolate` | `true` | Smooth position/angle interpolation between keyframes |

## Example

```kotlin
val doorAnim = blockAnimation(instance) {
    intervalTicks = 3
    frame { put(Pos(5, 65, 5), Block.AIR) }
    frame { put(Pos(5, 65, 5), Block.OAK_DOOR) }
}
doorAnim.start()

val visualOnly = blockAnimation(instance) {
    packetOnly = true
    loop = true
    frame { put(Pos(0, 65, 0), Block.REDSTONE_LAMP.withProperty("lit", "true")) }
    frame { put(Pos(0, 65, 0), Block.REDSTONE_LAMP.withProperty("lit", "false")) }
}
visualOnly.start()

val patrol = entityAnimation(npc) {
    loop = true
    interpolate = true
    keyframe(Pos(0.0, 65.0, 0.0, 0f, 0f))
    keyframe(Pos(10.0, 65.0, 0.0, 90f, 0f))
    keyframe(Pos(10.0, 65.0, 10.0, 180f, 0f))
}
patrol.start()
```

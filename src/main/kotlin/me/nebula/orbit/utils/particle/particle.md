# Particle

Unified particle system: reusable `ParticleEffect` builder, geometric shapes via `ParticleShape`, and convenience extension functions.

## ParticleEffect

Immutable particle config created via DSL.

```kotlin
val effect = particleEffect(Particle.FLAME) {
    count = 10
    offset(0.5f, 0.5f, 0.5f)
    speed = 0.1f
}

effect.spawn(instance, Pos(0.0, 65.0, 0.0))
effect.spawn(player, Pos(0.0, 65.0, 0.0))
effect.circle(instance, center = Pos(0.0, 65.0, 0.0), radius = 3.0, points = 20)
effect.line(instance, from = Pos(0.0, 65.0, 0.0), to = Pos(10.0, 65.0, 0.0), density = 0.5)
```

### ParticleBuilder Properties

| Property | Default | Description |
|---|---|---|
| `count` | `1` | Particle count per spawn |
| `offsetX/Y/Z` | `0f` | Random offset spread |
| `speed` | `0f` | Particle speed/data |

## Extension Functions

Quick one-off particle spawns without building a `ParticleEffect`.

```kotlin
instance.spawnParticleAt(Particle.FLAME, position, count = 5, spread = 0.3f, speed = 0.05f)
instance.spawnParticleLine(Particle.FLAME, from, to, density = 0.5, count = 1)
instance.spawnParticleCircle(Particle.FLAME, center, radius = 3.0, points = 20, count = 1)
instance.spawnBlockBreakParticle(position, count = 10)

player.spawnParticle(Particle.FLAME, position, count = 5, spread = 0.3f, speed = 0.05f)
```

## ParticleShape

Sealed interface for geometric shapes. Five variants:

| Shape | Parameters |
|---|---|
| `Circle` | `center: Pos`, `radius: Double`, `points: Int`, `particle: Particle` |
| `Sphere` | `center: Pos`, `radius: Double`, `density: Int`, `particle: Particle` |
| `Helix` | `base: Pos`, `radius: Double`, `height: Double`, `turns: Int`, `particle: Particle` |
| `Line` | `from: Pos`, `to: Pos`, `density: Double`, `particle: Particle` |
| `Cuboid` | `min: Pos`, `max: Pos`, `density: Double`, `particle: Particle` |

### ParticleShapeRenderer

Computes points for any shape and renders via packets.

```kotlin
val points: List<Pair<Pos, Particle>> = ParticleShapeRenderer.computePoints(shape)
ParticleShapeRenderer.render(instance, shape)
ParticleShapeRenderer.render(player, shape)
```

## showParticleShape DSL

Compose multiple shapes in one call. Available on `Player` and `Instance`.

```kotlin
player.showParticleShape {
    circle(center = Pos(0.0, 65.0, 0.0), radius = 3.0, points = 30, particle = Particle.FLAME)
    sphere(center = Pos(0.0, 70.0, 0.0), radius = 2.0, density = 15)
    helix(base = Pos(5.0, 65.0, 5.0), radius = 1.0, height = 5.0, turns = 4)
    line(from = Pos(0.0, 65.0, 0.0), to = Pos(10.0, 70.0, 10.0), density = 0.3)
    cuboid(min = Pos(0.0, 65.0, 0.0), max = Pos(5.0, 70.0, 5.0), density = 0.5)
}

instance.showParticleShape {
    circle(center = Pos(0.0, 65.0, 0.0), radius = 5.0)
}
```

### ParticleShapeBuilder Methods

| Method | Defaults | Description |
|---|---|---|
| `circle(center, radius, points, particle)` | points=20, particle=FLAME | Horizontal circle |
| `sphere(center, radius, density, particle)` | density=10, particle=FLAME | Fibonacci sphere |
| `helix(base, radius, height, turns, particle)` | turns=3, particle=FLAME | Vertical helix |
| `line(from, to, density, particle)` | density=0.5, particle=FLAME | Point-to-point line |
| `cuboid(min, max, density, particle)` | density=0.5, particle=FLAME | Wireframe cuboid edges |

## Animated Particles

Shapes with per-tick animation state: Y-axis rotation, radial expansion, and vertical rise. Base points are computed once at creation; `tick()` advances animation state; `render()` applies transformations and spawns packets.

### AnimatedParticleShape

| Property | Default | Description |
|---|---|---|
| `particle` | — | Particle type |
| `count` | — | Particles per point |
| `basePoints` | — | Pre-computed `Vec` offsets from origin |
| `rotationSpeed` | `0.0` | Radians per tick (Y-axis) |
| `expansionSpeed` | `0.0` | Scale multiplier delta per tick |
| `riseSpeed` | `0.0` | Y offset delta per tick |

Methods: `tick()`, `reset()`, `render(instance, origin)`, `render(player, origin)`.

### DSL Builders

```kotlin
val circle = animatedCircle {
    particle(Particle.FLAME)
    radius(2.0)
    points(32)
    rotateSpeed(0.1)
}

val helix = animatedHelix {
    particle(Particle.END_ROD)
    radius(1.0)
    height(3.0)
    turns(4)
    rotateSpeed(0.2)
    riseSpeed(0.05)
}

val sphere = animatedSphere {
    particle(Particle.SOUL_FIRE_FLAME)
    radius(1.5)
    density(8)
    rotateSpeed(0.05)
    expansionSpeed(0.01)
}
```

### Builder Methods

| Builder | Method | Default | Description |
|---|---|---|---|
| All | `particle(Particle)` | FLAME | Particle type |
| All | `count(Int)` | 1 | Particles per point |
| All | `rotateSpeed(Double)` | 0.0 | Y-axis rotation per tick |
| All | `expansionSpeed(Double)` | 0.0 | Radial scale delta per tick |
| All | `riseSpeed(Double)` | 0.0 | Y offset delta per tick |
| Circle | `radius(Double)` | 1.0 | Circle radius |
| Circle | `points(Int)` | 20 | Point count |
| Helix | `radius(Double)` | 1.0 | Helix radius |
| Helix | `height(Double)` | 3.0 | Helix height |
| Helix | `turns(Int)` | 3 | Number of turns |
| Sphere | `radius(Double)` | 1.0 | Sphere radius |
| Sphere | `density(Int)` | 10 | Fibonacci point density |

### Entity Attachment

Attach an animated shape to any entity. Ticks and renders automatically at entity position every tick.

```kotlin
val shape = animatedCircle {
    particle(Particle.FLAME)
    radius(1.5)
    points(24)
    rotateSpeed(0.15)
}

val attachment = entity.attachParticle(shape)
attachment.detach()
```

`ParticleAttachment.detach()` cancels the repeating task and resets animation state.

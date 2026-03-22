# Entity Builder — Smart Entity System

Priority-based behavior AI with type-safe memory, spatial sensors, and ModelEngine integration. MobMind-inspired architecture built for Nebula's scale.

## Quick Start

```kotlin
val zombie = spawnSmartEntity(EntityType.ZOMBIE, instance, spawnPos) {
    health(30f)
    speed(0.15)
    attack(5.0)
    hostile()
}
```

## Full Custom Entity

```kotlin
val boss = spawnSmartEntity(EntityType.BLAZE, instance, pos) {
    health(100f)
    speed(0.12)
    attack(8.0)
    model("iron_guardian")

    nearestEntitySensor(range = 24.0, predicate = { it is Player })

    behavior("attack") {
        priority(3)
        evaluateWhen { it.memory.has(MemoryKeys.ATTACK_TARGET) }
        executor(MeleeAttackExecutor(attackRange = 3.0, damage = 8f))
    }
    behavior("roam") {
        priority(1); weight(3)
        executor(FlatRoamExecutor(range = 12.0))
    }
    behavior("idle") {
        priority(1); weight(1)
        executor(IdleExecutor())
    }

    walkController()
    lookController()

    onDamage { entity ->
        entity.memory.set(MemoryKeys.PANIC_TICKS, 40)
        entity.playAnimation("hit")
    }
}
```

## Architecture

### Tick Pipeline (per entity, every server tick)

```
Sensors → Core Behaviors → Normal Behaviors → Execute Running → Controllers
   ↓            ↓                  ↓                 ↓              ↓
 Memory     Always-on       Priority-based      execute()      Walk/Look
 writes     never preempted  preemption          returns false   from Memory
                                                 → stop
```

### Priority Preemption

Higher priority behaviors interrupt lower priority ones. Equal priority coexists. Within same priority, weighted random selects one.

```kotlin
behavior("panic")  { priority(4) }  // interrupts everything below
behavior("attack") { priority(3) }  // interrupts follow/idle
behavior("follow") { priority(2) }  // interrupts idle
behavior("idle")   { priority(1); weight(1) }  // lowest
behavior("roam")   { priority(1); weight(3) }  // 75% chance vs idle
```

### Core Behaviors

Never preempted, always evaluate independently:
```kotlin
behavior("breathe") {
    core()
    executor(...)
}
```

## Components

### SmartEntity

Extends `EntityCreature`. Has:
- `memory: MemoryStorage` — type-safe concurrent storage
- `behaviorGroup: BehaviorGroup` — brain (sensors + behaviors + controllers)
- `modeledEntity: ModeledEntity?` — optional ModelEngine model
- Auto viewer management for models (`updateNewViewer`/`updateOldViewer`)
- `playAnimation(name, priority)` / `stopAnimation(name)`

### Memory

```kotlin
entity.memory.set(MemoryKeys.MOVE_TARGET, targetPos)
entity.memory.get(MemoryKeys.ATTACK_TARGET)
entity.memory.has(MemoryKeys.PANIC_TICKS)
entity.memory.clear(MemoryKeys.MOVE_TARGET)
```

Built-in keys: `MOVE_TARGET`, `LOOK_TARGET`, `ATTACK_TARGET`, `NEAREST_PLAYER`, `PANIC_TICKS`

Custom keys:
```kotlin
val MY_TARGET = MemoryKey<Player>("my_custom_target")
entity.memory.set(MY_TARGET, player)
```

### Sensors

Spatial queries via `instance.getNearbyEntities()` — O(nearby) not O(all):

```kotlin
nearestPlayerSensor(range = 32.0, period = 20)
nearestEntitySensor(range = 16.0, target = MemoryKeys.ATTACK_TARGET, predicate = { it is Player })
sensor(MyCustomSensor())
```

### Controllers

```kotlin
walkController()  // reads MOVE_TARGET → Minestom navigator
lookController()  // reads LOOK_TARGET → yaw + pitch
controller(MyCustomController())
```

### Built-in Executors

| Executor | Description |
|---|---|
| `IdleExecutor(min, max)` | Stand still for random duration |
| `FlatRoamExecutor(range, speed, runTicks)` | Random XZ movement on solid ground |
| `LookAroundExecutor(min, max)` | Random yaw for random duration |
| `FollowEntityExecutor(memoryKey, speed, minRange, maxRange)` | Follow entity in memory |
| `MeleeAttackExecutor(range, cooldown, damage)` | Chase + melee with cooldown |
| `PanicExecutor(speedMultiplier, range)` | Flee randomly while PANIC_TICKS > 0 |

### Presets

```kotlin
hostile()                    // attack nearest player + roam/idle
hostile(attackDamage = 8f)   // custom damage
passive()                    // look at player + roam/idle
passive(strollRange = 20.0)  // wider roam
```

## ModelEngine Integration

```kotlin
spawnSmartEntity(EntityType.ZOMBIE, instance, pos) {
    model("my_model")  // auto-creates ModeledEntity from blueprint
    // ...behaviors...
    onSpawn { entity ->
        entity.playAnimation("spawn", priority = 2)
    }
}
```

The model is auto-attached on spawn. Viewers are auto-managed — when a player enters/leaves view distance, the model is shown/hidden automatically.

Access the model:
```kotlin
entity.model()?.animationHandler?.play("walk")
entity.modeledEntity?.headYaw = entity.position.yaw()
```

## Custom Executor

```kotlin
class TeleportBehindExecutor : BehaviorExecutor {
    override fun onStart(entity: SmartEntity) {
        val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) ?: return
        val behind = target.position.add(target.position.direction().mul(-2.0))
        entity.teleport(behind)
        entity.playAnimation("teleport")
    }

    override fun execute(entity: SmartEntity): Boolean = false // one-shot
}
```

## Custom Sensor

```kotlin
class LowHealthSensor(override val period: Int = 10) : Sensor {
    override fun sense(entity: SmartEntity) {
        if (entity.health < entity.maxHealth * 0.3f) {
            entity.memory.set(MemoryKeys.PANIC_TICKS, 60)
        }
    }
}
```

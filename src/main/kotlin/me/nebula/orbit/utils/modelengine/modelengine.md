# Model Engine

Minestom-native custom entity model system. Renders Blockbench models as `ITEM_DISPLAY` entity hierarchies via packets. Supports animations, bone behaviors, mounting, VFX, LOD, and resource pack generation.

## Architecture

- **Packet-based**: All bones are virtual entities (negative IDs starting at `-3,000,000`), no real entities spawned
- **Per-player visibility**: `show(player)` / `hide(player)` on every model
- **20 TPS tick loop**: `ModelEngine.install()` starts a 1-tick repeat task
- **Blueprint → ActiveModel → ModeledEntity**: Immutable blueprint parsed once, instances created per entity
- **Tick order**: `computeTransforms()` → `tickBehaviors()` → `updateRenderer()` (behaviors see current-frame transforms)
- **Head tracking**: Set `modeledEntity.headYaw`/`headPitch` externally for HeadBehavior to react (NOT auto-set from body yaw)

## Quick Start

```kotlin
// 1. Install the engine
ModelEngine.install()

// 2. Register a blueprint (from JSON)
val blueprint = BlueprintLoader.load("dragon", FileReader("dragon.json"))
ModelEngine.registerBlueprint("dragon", blueprint)

// 3. Attach to an entity
val modeled = modeledEntity(entity) {
    model("dragon") {
        scale(2.0f)
    }
}

// 4. Show to a player
modeled.show(player)
```

## ModelOwner Interface

`ModelOwner` decouples `ModeledEntity` from `Entity`. Any object providing `position`, `isRemoved`, and `ownerId` can own a model.

```kotlin
interface ModelOwner {
    val position: Pos
    val isRemoved: Boolean
    val ownerId: Int
}
```

- **`EntityModelOwner`** — thin adapter wrapping a real `Entity`
- **`Entity.asModelOwner()`** — extension to wrap an entity
- **`StandaloneModelOwner`** — lightweight position-only owner (no real entity)

### Entity-backed (default)
```kotlin
val modeled = modeledEntity(entity) { model("dragon") {} }
```

### Standalone (no entity)
```kotlin
val dragon = standAloneModel(Pos(100.0, 65.0, 100.0)) {
    model("dragon") { scale(2f) }
}
dragon.show(player)
dragon.position = newPos  // mutable position
dragon.hide(player)
dragon.remove()           // destroys model + unregisters from ModelEngine
```

### Custom ModelOwner
```kotlin
val modeled = modeledEntity(myCustomOwner) { model("blueprint") {} }
```

Entity-only features (mount controllers, root motion, leash) gracefully degrade: `modeledEntity.entityOrNull` returns `null` for non-Entity owners, and those systems return early or fail fast.

## Blueprint Loading

### From simplified JSON
```kotlin
val blueprint = BlueprintLoader.load("name", reader)
ModelEngine.registerBlueprint("name", blueprint)
```

### From .bbmodel (full pipeline)
```kotlin
ModelIdRegistry.init(File("data/model_ids.txt"))
val result = ModelGenerator.generate(File("model.bbmodel"), File("output/"))
// result.blueprint is auto-registered
// result.packBytes is the resource pack zip
```

## Animation

### Priority-based
```kotlin
val handler = PriorityHandler()
handler.boundModel = model
handler.play("walk", lerpIn = 0.2f, lerpOut = 0.2f, speed = 1f)
handler.play("attack", lerpIn = 0.1f, lerpOut = 0.1f)  // higher priority
handler.tick(model, deltaSeconds)
```

### State Machine
```kotlin
val asm = animationStateMachine {
    state("idle", "animation.idle", lerpIn = 0.3f)
    state("walk", "animation.walk", lerpIn = 0.2f)
    state("attack", "animation.attack", lerpIn = 0.1f)
    transition("idle", "walk") { model -> isMoving(model) }
    transition("walk", "idle") { model -> !isMoving(model) }
    transition("walk", "attack") { model -> isAttacking(model) }
}

val handler = StateMachineHandler()
handler.addLayer(0, asm)  // base layer
handler.tick(model, deltaSeconds)
```

## Bone Behaviors

Behaviors are auto-created from blueprint definitions and auto-ticked by `ModeledEntity.tick()`. Lifecycle: `onAdd` on `addModel()`, `tick` every game tick, `onRemove` on `removeModel()`/`destroy()`. Behaviors with per-player viewer sets (`MountBehavior`, `NameTagBehavior`, `PlayerLimbBehavior`) implement `evictViewer(uuid)` for session cleanup.

| Type | Class | Purpose |
|---|---|---|
| HEAD | `HeadBehavior` | Smooth look-at rotation from entity head yaw/pitch |
| MOUNT | `MountBehavior` | Passenger seat via virtual Interaction entity |
| NAMETAG | `NameTagBehavior` | Virtual TextDisplay at bone position |
| HELD_ITEM | `HeldItemBehavior` | Override bone display item |
| GHOST | `GhostBehavior` | Hide bone visually |
| SEGMENT | `SegmentBehavior` | Chain IK constraint solving |
| SUB_HITBOX | `SubHitboxBehavior` | OBB hitbox with damage multiplier |
| LEASH | `LeashBehavior` | Delegates to EntityLeash util |
| PLAYER_LIMB | `PlayerLimbBehavior` | Player skin on bone |

```kotlin
// Behaviors are created from blueprint automatically:
val modeled = modeledEntity(entity) { model("dragon") {} }

// Access behaviors on bones:
val headBone = modeled.models["dragon"]!!.bone("head")
val head = headBone.behavior<HeadBehavior>()

// Add behaviors at runtime:
val bone = model.bone("body")
val hitbox = SubHitboxBehavior(bone, halfExtents = Vec(0.5, 1.0, 0.5))
bone.addBehavior(hitbox)
hitbox.onAdd(modeledEntity)
```

## Mount Controllers

```kotlin
// Mount a player onto a bone seat
MountManager.mount(player, modeledEntity, mountBehavior, WalkingController(speed = 0.2))

// Or flying
MountManager.mount(player, modeledEntity, mountBehavior, FlyingController(speed = 0.3))

// Dismount
MountManager.dismount(player)
```

## Interaction (Ray-cast)

```kotlin
val hit = ModelInteraction.raycast(player, maxDistance = 5.0)
if (hit != null) {
    val damage = baseDamage * hit.hitbox.damageMultiplier
    // Apply damage...
}
```

## VFX

```kotlin
val effect = vfx(ItemStack.of(Material.DIAMOND_SWORD)) {
    position(Vec(0.0, 5.0, 0.0))
    scale(2.0)
    lifetime(60) // 3 seconds
}
VFXRegistry.register(effect)
effect.show(player)
```

## LOD (Level of Detail)

```kotlin
val lod = lodConfig {
    level(16.0, tickRate = 1) // full detail within 16 blocks
    level(32.0, tickRate = 2) { hide("fingers", "toes") } // reduced detail
    level(48.0, tickRate = 4) { showOnly("body", "head") } // minimal bones
    cullDistance(64.0) // hide beyond 64 blocks
}
val lodHandler = LODHandler(lod)
lodHandler.evaluate(modeledEntity)
```

## Root Motion

```kotlin
val rootMotion = RootMotion("root", applyX = true, applyZ = true)
// Each tick:
rootMotion.tick(modeledEntity)
```

## Serialization

```kotlin
val data = ModelSerializer.serialize(modeledEntity)
// Later...
val restored = ModelSerializer.deserialize(entity, data)
```

## Resource Pack Generation

```kotlin
ModelIdRegistry.init(File("data/model_ids.txt"))
val result = ModelGenerator.generate(File("models/dragon.bbmodel"), File("output/packs/"))
// result.packBytes = zip file contents
// result.blueprint = registered ModelBlueprint
// result.boneCount = number of bone models generated
```

## Entity ID Ranges

| System | Range |
|---|---|
| NPCs | `-2,000,000` downward |
| Holograms | `-1,000,000` downward |
| Model Engine bones | `-3,000,000` downward |
| Mount seats | `-3,500,000` downward |
| Name tags | `-3,600,000` downward |
| Player limbs | `-3,700,000` downward |
| VFX | `-3,800,000` downward |
| Standalone models | `-4,000,000` downward |

## File Structure

```
utils/modelengine/
├── ModelEngine.kt                 # Singleton registry, factory DSL, tick loop
├── math/ModelMath.kt              # Quaternion, OBB, interpolation math
├── blueprint/
│   ├── ModelBlueprint.kt          # Immutable model data
│   ├── BlueprintBone.kt           # Static bone definition
│   └── BlueprintLoader.kt         # JSON loader
├── bone/
│   ├── BoneTransform.kt           # Position/rotation/scale + parent combine
│   └── ModelBone.kt               # Runtime mutable bone
├── render/BoneRenderer.kt         # Per-player ItemDisplay packets
├── model/
│   ├── ModelOwner.kt              # ModelOwner interface + EntityModelOwner adapter
│   ├── ActiveModel.kt             # Model instance from blueprint
│   └── ModeledEntity.kt           # Owner wrapper, DSL
├── animation/
│   ├── AnimationHandler.kt        # Sealed interface
│   ├── PriorityHandler.kt         # Priority-based blending
│   ├── StateMachineHandler.kt     # Layered state machines
│   ├── AnimationStateMachine.kt   # States + transitions
│   ├── AnimationProperty.kt       # Per-bone animation state
│   ├── KeyframeInterpolator.kt    # TreeMap-based interpolator
│   └── InterpolationFunctions.kt  # linear, catmullrom, bezier, step
├── behavior/
│   ├── BoneBehavior.kt            # Sealed interface
│   ├── HeadBehavior.kt            # Look-at rotation
│   ├── MountBehavior.kt           # Passenger seat
│   ├── NameTagBehavior.kt         # TextDisplay name
│   ├── HeldItemBehavior.kt        # Item override
│   ├── GhostBehavior.kt           # Invisible bone
│   ├── SegmentBehavior.kt         # Chain IK
│   ├── SubHitboxBehavior.kt       # OBB hitbox
│   ├── LeashBehavior.kt           # Leash anchor
│   ├── PlayerLimbBehavior.kt      # Player skin limb
│   └── BoneBehaviorFactory.kt     # Factory from type + config
├── interaction/
│   ├── ModelInteraction.kt        # Ray-cast against OBBs
│   └── ModelDamageEvent.kt        # Custom damage event
├── mount/
│   ├── MountManager.kt            # Driver/passenger orchestration
│   ├── MountController.kt         # Sealed interface
│   ├── WalkingController.kt       # Ground movement
│   └── FlyingController.kt        # Flying movement
├── vfx/
│   ├── VFX.kt                     # Standalone ItemDisplay effect
│   └── VFXRegistry.kt             # Lifecycle management
├── lod/
│   ├── LODLevel.kt                # Distance config + DSL
│   └── LODHandler.kt              # Per-player distance evaluation
├── advanced/
│   ├── RootMotion.kt              # Root bone delta → entity position
│   └── ModelSerializer.kt         # Save/load as ByteArray
├── generator/
│   ├── BlockbenchModel.kt         # bbmodel data classes
│   ├── BlockbenchParser.kt        # .bbmodel JSON parser
│   ├── ModelGenerator.kt          # Orchestrator: parse → blueprint + pack
│   ├── AtlasManager.kt            # Texture atlas stitching
│   ├── ModelIdRegistry.kt         # Persistent custom model data IDs
│   └── PackWriter.kt              # Resource pack zip writer
└── modelengine.md                 # This file
```

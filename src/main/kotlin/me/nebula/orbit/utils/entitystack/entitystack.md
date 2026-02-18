# EntityStack

Stack entities on top of each other as passengers with add/remove operations.

## Usage

```kotlin
val stack = EntityStackManager.createStack(instance, position, EntityType.ZOMBIE)
stack.addRider(Entity(EntityType.SKELETON))
stack.addRider(Entity(EntityType.SPIDER))

val top = stack.removeTop()
stack.size // 2

stack.despawn()
EntityStackManager.clear()
```

## Key API

- `EntityStackManager.createStack(instance, position, baseType)` — create a new entity stack
- `EntityStackManager.getStack(baseEntity)` — get stack by base entity
- `EntityStackManager.removeStack(baseEntity)` — despawn and remove a stack
- `EntityStackManager.all()` — all active stacks
- `EntityStackManager.clear()` — despawn and remove all stacks
- `StackedEntity.addRider(entity)` — add an entity on top of the stack
- `StackedEntity.removeTop()` — remove and return the topmost entity
- `StackedEntity.removeAll()` — remove all riders
- `StackedEntity.despawn()` — remove all riders and the base entity
- `StackedEntity.size` — total entity count (base + riders)

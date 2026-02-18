# EntityAI

DSL for configuring Minestom entity AI with presets and custom goal/target selectors.

## Quick Setup

```kotlin
creature.configureAI { hostile() }

creature.configureAI { passive() }

creature.configureAI { neutral() }
```

## Factory Functions

| Function | Description |
|---|---|
| `hostileCreature(EntityType)` | Create a creature with hostile AI |
| `passiveCreature(EntityType)` | Create a creature with passive AI (stroll + look around) |
| `neutralCreature(EntityType)` | Create a creature with neutral AI (attacks last damager only) |

## Custom AI Group

```kotlin
creature.configureAI {
    aiGroup {
        meleeAttack(speed = 1.6, cooldownMs = 1000)
        randomStroll(radius = 10)
        randomLookAround(chancePerTick = 20)
        targetClosest(range = 32f, targetType = Player::class.java)
        targetLastDamager(range = 16f)
        goal(customGoalSelector)
        target(customTargetSelector)
    }
}
```

## AI Presets

| Preset | Goals | Targets |
|---|---|---|
| `hostile(range, attackSpeed, attackCooldownMs)` | melee attack, random stroll, random look | closest entity, last damager |
| `passive(strollRadius)` | random stroll, random look | none |
| `neutral(range, attackSpeed, strollRadius)` | melee attack, random stroll, random look | last damager |

## Example

```kotlin
val zombie = hostileCreature(EntityType.ZOMBIE)
zombie.setInstance(instance, Pos(0.0, 65.0, 0.0))

val cow = passiveCreature(EntityType.COW)
cow.setInstance(instance, Pos(10.0, 65.0, 10.0))

val wolf = EntityCreature(EntityType.WOLF)
wolf.configureAI {
    aiGroup {
        meleeAttack(speed = 2.0)
        randomStroll(radius = 15)
        targetLastDamager(range = 20f)
    }
}
wolf.setInstance(instance, Pos(5.0, 65.0, 5.0))
```

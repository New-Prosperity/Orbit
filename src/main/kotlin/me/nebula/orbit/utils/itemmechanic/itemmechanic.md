# ItemMechanic

Attach custom behaviors to items identified by `ITEM_ID_TAG`. Hooks fire automatically when players use, attack with, get hurt while holding/wearing, or break blocks with tagged items.

## Interface

```kotlin
interface ItemMechanic {
    fun onUse(player: Player) {}
    fun onAttack(attacker: Player, target: Player, event: EntityDamageEvent) {}
    fun onHurt(victim: Player, attacker: Player?, event: EntityDamageEvent) {}
    fun onBlockBreak(player: Player, event: PlayerBlockBreakEvent) {}
}
```

## Registration

### DSL (preferred)

```kotlin
itemMechanic("ruby_sword") {
    onUse { player ->
        player.addEffect(Potion(PotionEffect.SPEED, 1, 100))
    }
    onAttack { attacker, target, event ->
        target.setOnFire(true)
    }
}
```

### Direct

```kotlin
ItemMechanicRegistry.register("ruby_sword", object : ItemMechanic {
    override fun onUse(player: Player) { /* ... */ }
})
```

## Lifecycle

```kotlin
ItemMechanicListener.install()    // registers global event node
ItemMechanicListener.uninstall()  // removes node + clears registry
```

## Event Hooks

| Hook | Trigger | Item checked |
|---|---|---|
| `onUse` | `PlayerUseItemEvent` | Used item |
| `onAttack` | `EntityDamageEvent` (EntityDamage, attacker=Player) | Attacker's main hand |
| `onHurt` | `EntityDamageEvent` (EntityDamage, target=Player) | Victim's armor + main hand |
| `onBlockBreak` | `PlayerBlockBreakEvent` | Player's main hand |

`onHurt` checks all 4 armor slots + main hand, calling distinct mechanics once each.

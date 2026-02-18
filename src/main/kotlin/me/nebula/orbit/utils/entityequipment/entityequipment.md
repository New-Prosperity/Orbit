# EntityEquipment

Entity equipment management with DSL builder, snapshot capture, and batch operations.

## Key Classes

- **`EquipmentSnapshot`** -- immutable capture of all 6 equipment slots, reapplyable
- **`EquipmentBuilder`** -- DSL builder for setting equipment slots

## Usage

```kotlin
entity.equip {
    helmet(ItemStack.of(Material.DIAMOND_HELMET))
    chestplate(ItemStack.of(Material.DIAMOND_CHESTPLATE))
    leggings(ItemStack.of(Material.DIAMOND_LEGGINGS))
    boots(ItemStack.of(Material.DIAMOND_BOOTS))
    mainHand(ItemStack.of(Material.DIAMOND_SWORD))
    offHand(ItemStack.of(Material.SHIELD))
}

val snapshot = entity.getEquipmentSnapshot()
snapshot.apply(otherEntity as LivingEntity)

entity.clearEquipment()
```

## Extension Functions

| Function | Description |
|----------|-------------|
| `Entity.equip {}` | DSL to set equipment slots (only non-AIR items applied) |
| `Entity.clearEquipment()` | Sets all 6 slots to AIR |
| `Entity.getEquipmentSnapshot()` | Captures current equipment as immutable snapshot |

## Details

- All extensions require the entity to be a `LivingEntity` (fail-fast with require)
- `equip {}` only sets slots that were explicitly configured (AIR slots skipped)
- `EquipmentSnapshot.apply()` overwrites all 6 slots on the target entity
- Snapshot is a data class for easy comparison and serialization

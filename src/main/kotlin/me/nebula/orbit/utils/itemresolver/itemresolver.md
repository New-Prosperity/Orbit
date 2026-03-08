# ItemResolver

Unified item resolution that checks `CustomItemRegistry` before falling back to `Material.fromKey()`. All custom items are automatically tagged with `ITEM_ID_TAG` for mechanic identification.

## Resolution

```kotlin
val stack = ItemResolver.resolve("ruby_sword")
val stack = ItemResolver.resolve("minecraft:iron_sword", 5)
val material = ItemResolver.resolveMaterial("ruby_sword")
```

Resolution order:
1. `CustomItemRegistry[key]` — returns `CustomItem.createStack()` (tagged with `ITEM_ID_TAG`)
2. `Material.fromKey(key)` — returns vanilla `ItemStack.of(material, amount)`
3. Throws if neither resolves

## Identification

```kotlin
ItemResolver.isCustom(stack)     // true if stack has ITEM_ID_TAG
ItemResolver.customId(stack)     // "ruby_sword" or null
```

## Kit Integration

`KitBuilder` has string-key overloads that delegate to `ItemResolver`:

```kotlin
kit("example") {
    helmet("ruby_helmet")
    chestplate("minecraft:diamond_chestplate")
    item(0, "ruby_sword")
    item(1, "minecraft:bow")
}
```

Config keys in `KitTierConfig`, `StarterKitItemConfig`, etc. can now reference custom content items directly — no code changes needed.

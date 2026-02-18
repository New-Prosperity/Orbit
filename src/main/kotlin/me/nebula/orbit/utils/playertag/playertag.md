# PlayerTag

Prefix/suffix tag system for player name formatting with priority-based stacking.

## DSL

```kotlin
playerTag(player) {
    id("admin")
    prefix("<red>[Admin] ")
    suffix(" <gray>[VIP]")
    nameColor(NamedTextColor.GOLD)
    priority(100)
}
```

## Extensions

```kotlin
player.addPlayerTag {
    id("donator")
    prefix("<green>[+] ")
    priority(50)
}
player.removePlayerTag("donator")
player.clearPlayerTags()
```

## Manager API

```kotlin
PlayerTagManager.addTag(player, tag)
PlayerTagManager.removeTag(player, "admin")
PlayerTagManager.getActiveTag(player)
PlayerTagManager.getAllTags(player)
PlayerTagManager.clearTags(player)
PlayerTagManager.cleanup(uuid)
```

## Behavior

- Multiple tags per player; highest priority tag determines display name.
- Tags applied to `customName`, `displayName` (visible in chat, tab, above head).
- `cleanup(uuid)` removes all tags for a disconnected player.

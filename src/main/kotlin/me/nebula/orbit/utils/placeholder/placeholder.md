# Placeholder

Dynamic placeholder registry that integrates with MiniMessage for per-player text resolution.

## PlaceholderRegistry

| Method | Description |
|---|---|
| `register(name, resolver)` | Register a placeholder with a `(Player) -> String` resolver |
| `unregister(name)` | Remove a placeholder |
| `resolve(text, player)` | Parse MiniMessage text with all placeholders resolved for the player |
| `resolveString(text, player)` | String-level `<name>` replacement (no MiniMessage parsing) |
| `resolverFor(player)` | Get a `TagResolver` for use with MiniMessage manually |
| `all()` | All registered placeholders |
| `names()` | All placeholder names |
| `clear()` | Remove all placeholders |

## Extension Function

| Function | Description |
|---|---|
| `player.resolvePlaceholders(text)` | Shortcut for `PlaceholderRegistry.resolve` |

## Example

```kotlin
PlaceholderRegistry.register("player_name") { it.username }
PlaceholderRegistry.register("player_health") { "%.0f".format(it.health) }
PlaceholderRegistry.register("online") {
    SessionStore.size.toString()
}

val component = player.resolvePlaceholders("<gold><player_name> <gray>HP: <red><player_health>")

val raw = PlaceholderRegistry.resolveString("Hello <player_name>!", player)

val resolver = PlaceholderRegistry.resolverFor(player)
val component2 = MiniMessage.miniMessage().deserialize("<player_name>", resolver)
```

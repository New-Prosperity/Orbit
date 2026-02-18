# BlockHighlight

Highlight blocks with glowing invisible shulker entities, auto-removed after a duration.

## Usage

```kotlin
player.highlightBlock(10, 65, 20, durationTicks = 200)

player.clearHighlights()

BlockHighlightManager.clear()
```

## Key API

- `Player.highlightBlock(x, y, z, durationTicks)` — spawn a glowing shulker at the block position
- `Player.clearHighlights()` — remove all highlights for the player
- `BlockHighlightManager.highlight(player, x, y, z, durationTicks)` — create a highlight entity
- `BlockHighlightManager.remove(key)` — remove a specific highlight
- `BlockHighlightManager.removeAll(player)` — remove all highlights for a player
- `BlockHighlightManager.clear()` — remove all highlights globally

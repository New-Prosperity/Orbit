# Title

Player extension functions for showing titles with MiniMessage formatting. Provides both a DSL builder and a convenience function.

## Key Classes

- **`TitleBuilder`** -- DSL builder for title/subtitle with timing

## Usage

### DSL

```kotlin
player.showTitle {
    title("<red>Game Over")
    subtitle("<gray>Better luck next time")
    fadeIn(200)
    stay(3000)
    fadeOut(500)
}
```

### Convenience Function

```kotlin
player.showTitle("<gold>Victory!", "<green>You won the match", fadeInMs = 500, stayMs = 3000, fadeOutMs = 500)
```

### Clear / Reset

```kotlin
player.clearTitle()
player.resetTitle()
```

Defaults: 500ms fade in, 3000ms stay, 500ms fade out.

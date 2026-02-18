# ClickableChat

Rich clickable chat message builder with MiniMessage formatting, click actions, and hover text.

## DSL

```kotlin
clickableMessage(player) {
    text("<gold>Click ")
    clickText("<aqua>[HERE]") {
        action(ClickAction.OPEN_URL, "https://example.com")
        hover("<gray>Click to visit website")
    }
    text(" to visit")
}
```

## Extension

```kotlin
player.sendClickable {
    text("<green>Run ")
    clickText("<yellow>/help") {
        action(ClickAction.RUN_COMMAND, "/help")
        hover("<gray>Click to run /help")
    }
}
```

## Building without sending

```kotlin
val component: Component = clickableMessage {
    text("<red>Copy: ")
    clickText("<white>[TOKEN]") {
        action(ClickAction.COPY_TO_CLIPBOARD, "abc123")
        hover("<gray>Click to copy")
    }
}
```

## Actions

- `RUN_COMMAND` -- executes a command as the clicking player.
- `SUGGEST_COMMAND` -- fills the chat box with the command text.
- `OPEN_URL` -- opens a URL in the player's browser.
- `COPY_TO_CLIPBOARD` -- copies text to clipboard.

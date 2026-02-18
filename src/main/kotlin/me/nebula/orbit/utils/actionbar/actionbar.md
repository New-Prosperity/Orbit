# Action Bar

Player extension functions for showing timed action bar messages with MiniMessage formatting.

## Key Functions

- **`Player.showActionBar(message, durationMs)`** -- display an action bar with auto-expiry (default 3s)
- **`Player.clearActionBar()`** -- immediately clear the action bar
- **`Player.hasActionBar()`** -- check if an action bar is currently active

## Usage

```kotlin
player.showActionBar("<gold>+50 coins", durationMs = 5000)

if (player.hasActionBar()) {
    player.clearActionBar()
}
```

The message is re-sent every second until the duration expires. Supports full MiniMessage formatting.

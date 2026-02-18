# Notification

Multi-channel notification system. Send messages to players via chat, action bar, title, boss bar, and sound simultaneously. Includes convenience broadcast functions.

## Channels

| Channel | Behavior |
|---|---|
| `CHAT` | `sendMessage` with message (falls back to title) |
| `ACTION_BAR` | `sendActionBar` with message (falls back to title) |
| `TITLE` | Title + subtitle with configurable fade times |
| `BOSS_BAR` | Temporary boss bar with progress drain over duration |
| `SOUND` | Plays configured sound event |

## notify DSL

```kotlin
notify(player) {
    title("<gold>Warning")
    message("<red>You are being attacked!")
    subtitle("<gray>Take cover")
    channels(NotificationChannel.CHAT, NotificationChannel.TITLE, NotificationChannel.SOUND)
    sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, volume = 1f, pitch = 1.5f)
    titleTimes(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
}
```

## buildNotification

Build a reusable `Notification` object.

```kotlin
val alert = buildNotification {
    title("<gold>Game Starting")
    message("<yellow>Prepare yourself!")
    channels(NotificationChannel.TITLE, NotificationChannel.SOUND)
}

alert.send(player)
alert.broadcast(instance)
alert.broadcastAll()
```

## Notification Methods

| Method | Description |
|---|---|
| `send(player)` | Send to a single player |
| `broadcast(instance)` | Send to all players in an instance |
| `broadcastAll()` | Send to all online players |

## NotificationManager

| Method | Description |
|---|---|
| `notifyPlayer(player, notification)` | Send notification to player |
| `notifyPlayer(player) {}` | DSL variant |
| `notifyInstance(instance, notification)` | Broadcast to instance |
| `notifyInstance(instance) {}` | DSL variant |
| `broadcast(notification)` | Broadcast to all players |
| `broadcast {}` | DSL variant |

## Boss Bar Options

```kotlin
notify(player) {
    title("<red>Alert")
    channels(NotificationChannel.BOSS_BAR)
    bossBar(color = BossBar.Color.RED, overlay = BossBar.Overlay.PROGRESS, duration = Duration.ofSeconds(10))
}
```

## Convenience Broadcast Functions

Quick one-line announcements. Pass `instance` to scope to that instance, or omit for all online players.

```kotlin
announceChat("<green>Server restarting in 5 minutes!")

announceChat("<yellow>Round starting!", instance)

announceActionBar("<red>GO!", instance)

announceTitle(
    title = "<gold>Victory!",
    subtitle = "<yellow>You won the game",
    fadeIn = 10,
    stay = 70,
    fadeOut = 20,
    instance = instance,
)
```

| Function | Parameters |
|---|---|
| `announceChat(message, instance?)` | MiniMessage chat broadcast |
| `announceActionBar(message, instance?)` | MiniMessage action bar broadcast |
| `announceTitle(title, subtitle?, fadeIn?, stay?, fadeOut?, instance?)` | Title broadcast; fade/stay in ticks (default 10/70/20) |

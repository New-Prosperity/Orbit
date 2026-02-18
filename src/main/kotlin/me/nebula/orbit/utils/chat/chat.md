# Chat

MiniMessage utilities for formatting and sending rich text messages.

## Core Functions

| Function | Description |
|---|---|
| `mm(text, resolvers...)` | Parse MiniMessage string to `Component` |
| `mm(text, placeholders)` | Parse with `Map<String, String>` placeholders |

## Extension Functions

| Function | Description |
|---|---|
| `Player.sendMM(text, resolvers...)` | Parse and send MiniMessage to player |
| `Player.sendMM(text, placeholders)` | Parse with map placeholders and send |
| `Player.sendPrefixed(prefix, message)` | Send prefixed MiniMessage |
| `Instance.broadcast(component)` | Send component to all players in instance |
| `Instance.broadcastMM(text, resolvers...)` | Parse and broadcast MiniMessage to instance |

## Global Broadcast

| Function | Description |
|---|---|
| `broadcastAll(component)` | Send to all online players |
| `broadcastAllMM(text, resolvers...)` | Parse and send to all online players |

## Message Builder

```kotlin
val msg = message {
    text("<gold>Welcome ")
    text("<white>to the server!")
    newLine()
    text("<gray>Enjoy your stay")
    component(someComponent)
    space()
}
```

## Example

```kotlin
val welcome = mm("<gradient:gold:yellow>Welcome to the server!")

player.sendMM("<green>You have <amount> coins", mapOf("amount" to "100"))

player.sendPrefixed("<gray>[Shop]", "<green>Item purchased!")

instance.broadcastMM("<red><player> has been eliminated!",
    Placeholder.parsed("player", player.username))

broadcastAllMM("<yellow>Server restarting in 5 minutes")
```

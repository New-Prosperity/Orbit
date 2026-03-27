# Profile Card

Renders formatted player stat cards as chat messages using MiniMessage.

## Key Classes

- **`ProfileLine`** -- one stat line (icon, label, value provider)
- **`ProfileCard`** -- immutable card definition (header, lines, footer, separator)
- **`ProfileCardBuilder`** -- DSL builder

## DSL

```kotlin
val card = profileCard {
    header { player -> "<gold>${player.username}'s Profile" }
    separator("<dark_gray><st>                              ")
    stat("\u2B50", "Level") { player -> "${player.level}" }
    stat("\u26C1", "Coins") { player -> "1000" }
    stat("\u2694", "Kills") { player -> "123" }
    stat("\u2605", "Wins") { player -> "45" }
    stat("\u2920", "K/D") { player -> "2.5" }
    footer { player -> "<gray>Member since 2024" }
}

card.sendTo(viewer, target)
val components: List<Component> = card.render(target)
```

## API

| Method | Description |
|--------|-------------|
| `profileCard { }` | DSL builder returning `ProfileCard` |
| `ProfileCard.render(target)` | Renders card as `List<Component>` evaluating providers against target |
| `ProfileCard.sendTo(viewer, target)` | Renders and sends card messages to viewer |

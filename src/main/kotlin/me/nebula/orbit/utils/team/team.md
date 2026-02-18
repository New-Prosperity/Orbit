# Team

Named team management via `TeamManager` singleton with DSL builder and player extensions.

## Key Classes

- **`TeamManager`** — singleton registry for named `Team` instances
- **`TeamBuilder`** — DSL builder for team properties

## Usage

```kotlin
val red = TeamManager.create("red") {
    displayName("<red>Red Team")
    color = NamedTextColor.RED
    prefix("<red>[RED] ")
    suffix("")
}

player.joinTeam(red)
player.leaveTeam(red)

TeamManager.get("red")
TeamManager.require("red")
TeamManager.delete("red")
TeamManager.all()
TeamManager.playerTeam(player)
```

## Builder Properties

| Property | Default | Description |
|----------|---------|-------------|
| `displayName` | team name | MiniMessage display name |
| `color` | `WHITE` | `NamedTextColor` for the team |
| `prefix` | empty | MiniMessage prefix before name |
| `suffix` | empty | MiniMessage suffix after name |

# CommandBuilder

Enhanced command builder DSL extending Minestom's command system. Supports aliases, permissions, player-only restriction, arguments, sub-commands, and tab completion.

## Key Classes

- **`CommandBuilderDsl`** -- DSL builder for command configuration
- **`command()`** -- top-level DSL entry point

## Usage

```kotlin
val cmd = command("teleport") {
    aliases("tp", "goto")
    permission("orbit.teleport")
    playerOnly()

    stringArgument("target")

    tabComplete { player, input ->
        MinecraftServer.getConnectionManager().onlinePlayers
            .map { it.username }
            .filter { it.startsWith(input.split(" ").last(), ignoreCase = true) }
    }

    onPlayerExecute { player, context ->
        val target = context.get<String>("target")
        player.sendMessage("Teleporting to $target")
    }
}

MinecraftServer.getCommandManager().register(cmd)
```

## Sub-Commands

```kotlin
val cmd = command("arena") {
    subCommand("create") {
        stringArgument("name")
        onPlayerExecute { player, context ->
            val name = context.get<String>("name")
            player.sendMessage("Created arena $name")
        }
    }

    subCommand("delete") {
        stringArgument("name")
        onPlayerExecute { player, context ->
            player.sendMessage("Deleted arena")
        }
    }
}
```

## Argument Types

| Method | Minestom Type |
|--------|--------------|
| `stringArgument(name)` | ArgumentType.String |
| `intArgument(name)` | ArgumentType.Integer |
| `doubleArgument(name)` | ArgumentType.Double |
| `floatArgument(name)` | ArgumentType.Float |
| `booleanArgument(name)` | ArgumentType.Boolean |
| `wordArgument(name)` | ArgumentType.Word |
| `stringArrayArgument(name)` | ArgumentType.StringArray |
| `argument(arg)` | Any custom Argument |

## Details

- `onExecute` receives raw `CommandSender` + `CommandContext`
- `onPlayerExecute` automatically sets `playerOnly()` and casts sender
- Tab completion applied to last argument's suggestion callback
- Sub-commands are full `Command` objects added recursively
- Permission check via Minestom's condition system
- Player-only commands reject non-Player senders silently

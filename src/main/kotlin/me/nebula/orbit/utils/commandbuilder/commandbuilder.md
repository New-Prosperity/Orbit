# CommandBuilder

Command DSL for Minestom with virtual-thread execution, locale-aware context, typed arguments, sub-commands, and tab completion.

## Key Classes

- **`CommandBuilderDsl`** -- DSL builder for command configuration
- **`CommandExecutionContext`** -- Player + args + locale context passed to `onPlayerExecute`
- **`command()`** -- top-level DSL entry point
- **`OnlinePlayerCache`** -- 5-second refreshing cache of online player names
- **`suggestPlayers(prefix)`** -- filter `OnlinePlayerCache` by prefix
- **`resolvePlayer(name)`** -- resolve player by name (online first, then PlayerStore)

## Usage

```kotlin
val cmd = command("teleport") {
    aliases("tp", "goto")
    permission("orbit.teleport")

    stringArgument("target")

    tabComplete { player, input ->
        suggestPlayers(input.split(" ").last())
    }

    onPlayerExecute {
        val target = args.get<String>("target")
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
        onPlayerExecute {
            val name = args.get<String>("name")
            player.sendMessage("Created arena $name")
        }
    }

    subCommand("delete") {
        stringArgument("name")
        onPlayerExecute {
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

- All handlers run on virtual threads (safe for blocking operations)
- `onPlayerExecute` provides `CommandExecutionContext` with `player`, `args`, and `locale`
- `onExecute` provides raw `CommandSender` + `CommandContext` (also on virtual thread)
- Permission check via `RankManager.hasPermission()` (Gravity network ranks)
- Tab completion applied to last argument's suggestion callback
- Sub-commands are full `Command` objects added recursively
- Player-only commands reject non-Player senders silently

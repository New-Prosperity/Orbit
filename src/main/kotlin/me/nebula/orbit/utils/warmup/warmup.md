# Warmup

Generic warmup/channel action system with cancel triggers and action bar progress.

## Usage

```kotlin
warmup(player, "Teleporting", Duration.ofSeconds(5)) {
    cancelOnMove()
    cancelOnDamage()
    cancelOnCommand()
    onTick { player, remaining -> }
    onComplete { player -> player.teleport(target) }
    onCancel { player, trigger -> player.sendMM("<red>Cancelled: $trigger") }
    showProgressBar(true)
    progressBarFormat("<yellow>{name} <white>{bar} <gray>{time}s")
}
```

## WarmupManager

```kotlin
WarmupManager.isWarming(player)           // Boolean
WarmupManager.remaining(player)           // Duration
WarmupManager.cancel(player, CancelTrigger.MANUAL)
WarmupManager.cancelAll()
WarmupManager.uninstall()                 // Remove all events and warmups
```

## Cancel Triggers

- `MOVE` - Player moves >0.5 blocks from origin
- `DAMAGE` - Player takes damage
- `COMMAND` - Player sends a chat message starting with /
- `MANUAL` - Programmatic cancellation

## Progress Bar

Shown via action bar by default. Customizable format with `{bar}`, `{name}`, `{time}` placeholders.

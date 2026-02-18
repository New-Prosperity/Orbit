# DeathMessage

Customizable death messages with per-cause templates, placeholder replacement, and broadcast scoping.

## Usage

```kotlin
val handler = deathMessages {
    pvp("<red>{victim} was slain by {killer} using {weapon}")
    fall("<yellow>{victim} fell to their death")
    void("<gray>{victim} fell into the void")
    generic("<gray>{victim} died")
    broadcastAll()
}
handler.install()
handler.uninstall()
```

## Key API

- `deathMessages { }` — DSL to build a `DeathMessageHandler`
- `pvp(template)` / `fall(template)` / `void(template)` / `fire(template)` / `drowning(template)` / `explosion(template)` / `projectile(template)` / `generic(template)` — set per-cause MiniMessage templates
- `broadcastAll()` — send to all online players
- `broadcastInstance()` — send only to players in the victim's instance
- Placeholders: `{victim}`, `{killer}`, `{weapon}`
- `DeathMessageHandler.install()` — register damage tracking and death event listeners
- `DeathMessageHandler.uninstall()` — remove listeners
- `DeathCause` — enum: PVP, FALL, VOID, FIRE, DROWNING, EXPLOSION, PROJECTILE, GENERIC

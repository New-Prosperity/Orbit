# Knockback

Configurable knockback profiles with per-player overrides and directional application.

## Usage

```kotlin
val profile = knockbackProfile("pvp") {
    horizontal = 0.5
    vertical = 0.45
    extraHorizontal = 0.1
    friction = 0.9
}
KnockbackManager.register(profile)
KnockbackManager.setPlayerProfile(player, "pvp")

target.applyKnockback(attacker, profile)
target.applyDirectionalKnockback(attacker.position.yaw(), profile)
```

## Key API

- `knockbackProfile(name) { }` — DSL to build a `KnockbackProfile`
- `KnockbackManager.register(profile)` — register a named profile
- `KnockbackManager.get(name)` — get a profile by name
- `KnockbackManager.setPlayerProfile(player, profileName)` — override profile for a player
- `KnockbackManager.clearPlayerProfile(player)` — remove player override
- `KnockbackManager.getEffectiveProfile(player)` — resolve active profile (override or default)
- `Entity.applyKnockback(source, profile)` — apply knockback away from source entity
- `Entity.applyDirectionalKnockback(yawDegrees, profile)` — apply knockback in a specific direction

# Command Alias

Simple command aliasing system for registering alternative command names.

## Overview

Creates aliases that forward to target commands with optional argument passthrough. Registered aliases execute the target command with any additional arguments provided by the user.

## Key API

- `registerAlias(alias: String, target: String)` - Register a single alias
- `registerAliases(vararg pairs: Pair<String, String>)` - Register multiple aliases at once

## Examples

```kotlin
registerAlias("warp", "teleport")
registerAlias("msg", "message")

registerAliases(
    "heal" to "minecraft:effect give @s minecraft:instant_health",
    "speed" to "minecraft:effect give @s minecraft:speed"
)
```

Users can then call `/warp <location>` which maps to `/teleport <location>`, or `/msg <player> <text>` which maps to `/message <player> <text>`.

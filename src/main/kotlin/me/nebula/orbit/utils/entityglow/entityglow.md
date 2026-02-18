# Entity Glow

Per-player and global entity glowing. Per-player glow uses `EntityMetaDataPacket` sent to the specific viewer, setting the glowing flag on the real target entity's shared metadata. A 20-tick refresh task ensures the glow persists across normal metadata updates.

## EntityGlowManager

| Method | Description |
|---|---|
| `setGlowing(viewer, target, glowing)` | Set per-player glow via metadata packet |
| `isGlowing(viewer, target)` | Check per-player glow state |
| `clearViewer(viewer)` | Remove all glow states for a viewer |
| `clearTarget(target)` | Remove all glow states tracking a target |
| `clearAll()` | Remove all glow states |
| `setGlobalGlowing(target, glowing)` | Set entity-level `isGlowing` |
| `setTimedGlowing(target, durationTicks)` | Glow for a duration then auto-remove |

## Extension Functions

| Function | Description |
|---|---|
| `player.setGlowingFor(target, glowing)` | Per-player glow shortcut |
| `player.isGlowingFor(target)` | Check per-player glow |
| `player.clearGlowViews()` | Clear all glow states for viewer |
| `entity.setGlobalGlow(glowing)` | Global glow shortcut |
| `entity.glowFor(durationTicks)` | Timed global glow |

## Example

```kotlin
player.setGlowingFor(targetEntity, true)

if (player.isGlowingFor(targetEntity)) {
    player.sendMM("<yellow>Target is highlighted!")
}

player.setGlowingFor(targetEntity, false)

entity.glowFor(100)
```

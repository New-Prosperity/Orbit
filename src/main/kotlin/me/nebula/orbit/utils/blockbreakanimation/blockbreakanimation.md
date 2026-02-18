# Block Break Animation

Send fake block break progress animation to players. Stages 0-9 show progressive crack textures.

## BlockBreakAnimationManager

| Method | Description |
|---|---|
| `startAnimation(instance, position, durationTicks)` | Start auto-progressing animation, returns animation ID |
| `cancelAnimation(animationId, instance, position)` | Cancel and remove animation |
| `cancelAll()` | Cancel all active animations |

## Extension Functions

| Function | Description |
|---|---|
| `instance.showBreakProgress(position, entityId, stage)` | Send single break stage packet |
| `instance.animateBlockBreak(position, durationTicks)` | Start auto-progressing animation |

## Example

```kotlin
val animId = instance.animateBlockBreak(blockPos, 40)

BlockBreakAnimationManager.cancelAnimation(animId, instance, blockPos)

instance.showBreakProgress(blockPos, entityId = 999, stage = 5)
```

## Notes

- Stage 0-9: progressive crack texture
- Stage -1: remove animation
- `startAnimation` automatically progresses through stages 0-9 over the given duration
- Animation auto-cleans up on completion

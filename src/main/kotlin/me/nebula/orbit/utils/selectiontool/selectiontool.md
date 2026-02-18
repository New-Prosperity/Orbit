# SelectionTool

WorldEdit-style position selection system with wand item and particle boundaries.

## Usage

```kotlin
SelectionManager.giveWand(player)

// Positions are set via left-click (pos1) and right-click (pos2) with the wand
// Particle outlines render automatically every 10 ticks

val selection = player.getSelection()  // Selection?
selection?.volume                      // Int
selection?.toRegion("myregion")       // CuboidRegion
```

## Player Extensions

```kotlin
player.setPos1(point)
player.setPos2(point)
player.getSelection()                 // Selection?
player.fillSelection(Block.STONE)     // Fill with block
player.clearSelection()               // Fill with AIR
player.countBlocks()                  // Map<Block, Int>
```

## SelectionManager

```kotlin
SelectionManager.giveWand(player)
SelectionManager.removeWand(player)
SelectionManager.getSelection(uuid)
SelectionManager.clearSelection(uuid)
SelectionManager.install()            // Auto-called on first giveWand
SelectionManager.uninstall()          // Remove events, particles, cleanup
```

## Selection Data

```kotlin
val sel = player.getSelection()!!
sel.pos1                              // Point
sel.pos2                              // Point
sel.min                               // Pos (normalized min corner)
sel.max                               // Pos (normalized max corner)
sel.sizeX / sizeY / sizeZ            // Int dimensions
sel.volume                            // Int
sel.contains(point)                   // Boolean
sel.toRegion("name")                  // CuboidRegion
```

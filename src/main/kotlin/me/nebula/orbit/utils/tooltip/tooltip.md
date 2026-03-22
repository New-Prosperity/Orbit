# Tooltip Style System

Custom tooltip borders using MC 1.21.2+ `tooltip_style` data component with generated 9-slice sprite textures.

## Built-in Styles

| Style | Border Color | Highlight |
|-------|-------------|-----------|
| `royal` | Gold (#D48C16) | Light gold (#E6C02C) |
| `epic` | Purple (#A020F0) | Light purple (#BE64FF) |
| `rare` | Blue (#0070DD) | Light blue (#50A0FF) |
| `legendary` | Orange (#FF8000) | Light orange (#FFB43C) |
| `mythic` | Red (#E6001A) | Light red (#FF5050) |
| `common` | Gray (#808080) | Light gray (#B4B4B4) |

## Usage

### Set tooltip style on an item
```kotlin
import me.nebula.orbit.utils.tooltip.withTooltipStyle

val item = ItemStack.of(Material.DIAMOND_SWORD)
    .withTooltipStyle("royal")
```

### Use with ItemBuilder
```kotlin
val item = itemStack(Material.DIAMOND_SWORD) {
    name("<gold>Excalibur")
    lore("<gray>A legendary blade")
}.withTooltipStyle("legendary")
```

### Register a custom style
```kotlin
import me.nebula.orbit.utils.tooltip.TooltipStyleRegistry
import me.nebula.orbit.utils.tooltip.TooltipStyleDef
import java.awt.Color

TooltipStyleRegistry.register(
    TooltipStyleDef(
        id = "divine",
        borderColor = Color(255, 215, 0),
        highlightColor = Color(255, 245, 150),
    )
)
```
Register custom styles BEFORE `CustomContentRegistry.init()` (in `Orbit.kt` startup) so the sprite textures are included in the resource pack.

### Clear tooltip style
```kotlin
import net.minestom.server.component.DataComponents

val plain = item.without(DataComponents.TOOLTIP_STYLE)
```

## Test Command

`/tooltip list` — show all registered styles
`/tooltip set <style>` — apply style to held item
`/tooltip clear` — remove style from held item

Permission: `orbit.tooltip`

## How It Works

1. `TooltipStyleRegistry.registerDefaults()` registers built-in styles at startup
2. `TooltipStylePack.generate()` creates 9-slice sprite PNGs + `.mcmeta` for each style
3. Sprites are placed at `assets/minecraft/textures/gui/sprites/tooltip/<style>/background.png` and `frame.png`
4. The vanilla GUI atlas (`gui.json`) auto-discovers sprites under `gui/sprites/`
5. Setting `DataComponents.TOOLTIP_STYLE` to `"minecraft:tooltip/<style>"` tells the client to use the custom sprites
6. Items without `TOOLTIP_STYLE` use the vanilla default tooltip

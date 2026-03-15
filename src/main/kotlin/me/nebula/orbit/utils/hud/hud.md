# HUD — Shader-Based HUD System

Boss bar text with custom bitmap font sprites repositioned by a modified `rendertype_text` vertex shader. Resolution/GUI scale independent via `ProjMat`-derived normalization. Coexists with map screen shaders.

## How It Works
1. Server shows boss bars per player (progress=0, overlay=PROGRESS — invisible bar, only title renders)
2. Boss bar title uses font `minecraft:hud` mapping PUA characters (U+E000+) to sprite textures
3. Per-character color encodes screen position: R=X%, G=Y%, B=254 (marker)
4. Modified `rendertype_text.vsh` detects marker (GUI mode + B==254), repositions vertices
5. Modified `rendertype_text.fsh` renders HUD sprites with texture color only

## Usage

### Define a Layout
```kotlin
val myHud = hudLayout("my-hud") {
    bar("health") {
        anchor(HudAnchor.BOTTOM_LEFT)
        offset(0.02f, 0.88f)
        sprites(bg = "bar_bg", fill = "bar_fill_red", empty = "bar_empty")
        segments(10)
    }
    bar("mana") {
        anchor(HudAnchor.BOTTOM_LEFT)
        offset(0.02f, 0.84f)
        sprites(bg = "bar_bg", fill = "bar_fill_blue", empty = "bar_empty")
        segments(10)
    }
    sprite("icon") {
        anchor(HudAnchor.TOP_CENTER)
        offset(0f, 0.02f)
        sprite("icon_health")
    }
    text("score") {
        anchor(HudAnchor.TOP_RIGHT)
        offset(-0.05f, 0.02f)
    }
    group("buffs") {
        anchor(HudAnchor.TOP_LEFT)
        offset(0.02f, 0.02f)
        horizontal(spacing = 0.01f)
    }
    animated("loading") {
        anchor(HudAnchor.CENTER)
        frames("loading_0", "loading_1", "loading_2", "loading_3")
        interval(5)
    }
}
HudManager.register(myHud)
```

### Show/Update/Hide
Multiple layouts can be active simultaneously — each gets its own boss bar.
```kotlin
player.showHud("my-hud")
player.showHud("compass-hud")        // stacks on top, independent lifecycle
player.updateHud("health", 7)        // 7 of 10 segments filled (auto-finds layout)
player.updateHud("score", "42")      // renders digit sprites
player.updateHud("status", "{icon_health}100%") // inline sprite + digits + glyph
player.updateHud("info", "3:45 {icon_fire}")    // digits + glyph + sprite
player.updateHud("my-hud", "health", 7) // explicit layout targeting
player.addHudIcon("buffs", "icon_speed")
player.removeHudIcon("buffs", "icon_speed")
player.hideHud("my-hud")             // hides specific layout
player.hideAllHuds()                  // hides everything
```

### Query State
```kotlin
player.isHudShowing("my-hud")        // Boolean
player.hud("my-hud")                 // PlayerHud?
player.huds                           // all active PlayerHud instances
player.activeHudIds                   // Set<String> of active layout IDs
```

## Element Types
| Type | DSL | Value | Description |
|---|---|---|---|
| `BarElement` | `bar("id") { ... }` | Int (filled segments) | Segmented bar with bg/fill/empty sprites |
| `SpriteElement` | `sprite("id") { ... }` | — | Single sprite at position |
| `TextElement` | `text("id") { ... }` | String (digits/glyphs/sprites) | Renders 0-9, :, /, ., %, spaces, and `{sprite_id}` inline sprites |
| `GroupElement` | `group("id") { ... }` | addHudIcon/removeHudIcon | Dynamic sprite list (buffs, debuffs) |
| `AnimatedSpriteElement` | `animated("id") { ... }` | — | Frame-cycling sprite animation |

## Anchors
`TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `CENTER_LEFT`, `CENTER`, `CENTER_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`

Offset is additive to anchor position (0-1 normalized coordinates).

## Pre-Registered Sprites
- **Bars**: `bar_bg`, `bar_fill_red`, `bar_fill_blue`, `bar_fill_green`, `bar_fill_yellow`, `bar_empty`
- **Icons**: `icon_health`, `icon_mana`, `icon_shield`, `icon_speed`, `icon_strength`, `icon_fire`
- **Borders**: `border_left`, `border_right`, `border_top`, `border_bottom`, `corner_tl`, `corner_tr`, `corner_bl`, `corner_br`
- **Digits**: `digit_0`..`digit_9`, `glyph_colon`, `glyph_slash`, `glyph_dot`, `glyph_percent`

## Custom Sprites
```kotlin
HudSpriteRegistry.registerFromImage("my_icon", bufferedImage)
```
Must be registered before `CustomContentRegistry.mergePack()`.

## Architecture
- `HudShaderPack` — generates combined `rendertype_text` shaders (HUD + map screen)
- `HudSpriteRegistry` — sprite definitions with PUA character assignments
- `HudFontProvider` — generates atlas PNG + font JSON
- `HudManager` — per-player state, boss bar lifecycle, tick updates
- `HudRenderer` — state → Adventure Component → boss bar title
- `HudExtensions` — Player extension functions

## Tick Rate
HUD renders every 2 ticks (100ms). Only sends boss bar updates when component changes (diff check).

## Boss Bar Coexistence
Regular boss bars (dragon health, custom gameplay bars) work alongside HUD layouts. The HUD marker (B=254) is not used by normal text colors, so boss bar titles render correctly.

**Shadow detection**: HUD character shadows (color×0.25, B≈63) are discarded to prevent ghost artifacts. A grayscale check excludes white/gray text shadows (R≈G≈B≈63) from being falsely discarded. Boss bar text with high-blue colors (aqua, blue, pink — §b, §9, §d) may lose their text shadow due to B×0.25≈63 collision. The text itself always renders correctly.

## Test Command
`/hud` — toggles test HUD layout on the executing player.

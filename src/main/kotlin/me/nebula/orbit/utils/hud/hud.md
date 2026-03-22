# HUD — Shader-Based HUD System

Boss bar text with custom bitmap font sprites repositioned by a modified `rendertype_text` vertex shader. Resolution/GUI scale independent via `ProjMat`-derived normalization. Coexists with map screen shaders.

## How It Works
1. Server shows boss bars per player (progress=0, overlay=PROGRESS — invisible bar, only title renders)
2. Boss bar title uses font `minecraft:hud` mapping PUA characters (U+E000+) to sprite textures
3. Per-character color encodes screen position: R=X%, G=Y%, B=254 (marker)
4. Modified `rendertype_text.vsh` detects marker (GUI mode + B==254), repositions vertices
5. Modified `rendertype_text.fsh` renders HUD sprites with texture color only (ignores vertex color tint)

## Resource Pack Integration

The HUD system auto-generates and merges these entries into the resource pack during `CustomContentRegistry.mergePack()`:

| Pack Path | Source | Purpose |
|---|---|---|
| `assets/minecraft/shaders/core/rendertype_text.vsh` | `HudShaderPack.generateVsh()` | Vertex shader with HUD repositioning + shadow discard |
| `assets/minecraft/shaders/core/rendertype_text.fsh` | `HudShaderPack.generateFsh()` | Fragment shader with HUD rendering + map decode |
| `assets/minecraft/shaders/include/map_decode.glsl` | `MapShaderPack.generate()` | Map texture decode functions (coexists with HUD) |
| `assets/nebula/textures/hud/sprites.png` | `HudFontProvider.generateAtlas()` | Sprite atlas (8×8 cells, 16 columns) |
| `assets/minecraft/font/hud.json` | `HudFontProvider.generateFontJson()` | Bitmap font provider mapping PUA chars → atlas cells |

**Nothing to configure manually.** All shader and font files are generated at runtime from registered sprites. The only manual step is creating custom sprite images if you want non-placeholder graphics.

### Custom Sprite Images

Default sprites are colored rectangles (bars, icons, borders) and 5×7 bitmap digits. To replace with real art:

```kotlin
val myImage = ImageIO.read(resourceStream) // 8×8 px BufferedImage
HudSpriteRegistry.registerFromImage("my_icon", myImage)
```

Or replace a built-in sprite's image:
```kotlin
val heartIcon = ImageIO.read(File("heart.png")) // 8×8 px
HudSpriteRegistry.registerFromImage("icon_health", heartIcon)
```

**Must be called before `CustomContentRegistry.mergePack()`** (i.e., during startup before pack generation).

### Shader Architecture

The vertex shader detects HUD characters by checking:
1. `ProjMat[2][3] == 0.0` — GUI rendering (orthographic projection)
2. `int(Color.b * 255 + 0.5) == 254` — HUD marker in blue channel

When both match, it:
- Computes GUI dimensions: `guiW = 2.0 / ProjMat[0][0]`, `guiH = -2.0 / ProjMat[1][1]`
- Decodes position: `targetX = Color.r * guiW`, `targetY = Color.g * guiH`
- Repositions the quad's 4 vertices to an 8×8 pixel square at (targetX, targetY)

Shadow detection: text shadows are colored ×0.25 by MC, so B=254 becomes B≈63. The shader detects B∈{63,64} and discards non-grayscale shadows (prevents ghost HUD artifacts). A grayscale check (`R≈G≈B`) preserves normal white/gray text shadows.

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

Offset is additive to anchor position (0-1 normalized coordinates). Final position is clamped to [0, 1].

## Position Resolution
- 256 steps per axis (R and G channels = 0-255)
- At 960×540 GUI size: ~3.75 GUI pixels per step
- Character step between adjacent HUD elements: `3/255 ≈ 0.012` (~8 GUI pixels)

## Pre-Registered Sprites (34 total)
- **Bars** (6): `bar_bg`, `bar_fill_red`, `bar_fill_blue`, `bar_fill_green`, `bar_fill_yellow`, `bar_empty`
- **Icons** (6): `icon_health`, `icon_mana`, `icon_shield`, `icon_speed`, `icon_strength`, `icon_fire`
- **Borders** (8): `border_left`, `border_right`, `border_top`, `border_bottom`, `corner_tl`, `corner_tr`, `corner_bl`, `corner_br`
- **Digits** (10): `digit_0`..`digit_9`
- **Glyphs** (4): `glyph_colon`, `glyph_slash`, `glyph_dot`, `glyph_percent`

## Custom Sprites
```kotlin
HudSpriteRegistry.registerFromImage("my_icon", bufferedImage) // 8×8 px image
```
Must be registered before `CustomContentRegistry.mergePack()`.

To add a custom sprite with a default placeholder (colored rect):
```kotlin
HudSpriteRegistry.register("my_status_icon") // gets auto-assigned PUA char + atlas cell
```

## Architecture
| File | Purpose |
|---|---|
| `shader/HudShaderPack.kt` | Generates `rendertype_text.vsh/fsh` (HUD + map decode). Delegates to `MapShaderPack` for map_decode.glsl |
| `font/HudSprite.kt` | `HudSpriteDefinition` data class + `HudSpriteRegistry` singleton. Maps sprite IDs to PUA chars (U+E000+) and atlas grid positions |
| `font/HudFontProvider.kt` | Generates sprite atlas PNG (8×8 cells, 16 columns) + MC bitmap font JSON (`minecraft:hud`). Default sprites: colored rects for bars/icons, 5×7 pixel bitmaps for digits/glyphs |
| `Hud.kt` | `HudAnchor`, `Direction`, sealed `HudElement` hierarchy, `HudLayout`, builder DSL (`hudLayout {}`, `bar {}`, `sprite {}`, `text {}`, `group {}`, `animated {}`) |
| `HudManager.kt` | `PlayerHud` per-player-per-layout state (values, groupItems, bossBar, lastRendered, animationTick). Singleton: register, show, hide, update, tick |
| `HudRenderer.kt` | State → Adventure `Component` → boss bar title. Position encoding via `TextColor(R=X*255, G=Y*255, B=254)` on font `minecraft:hud` |
| `HudExtensions.kt` | Player extension functions: `showHud`, `hideHud`, `updateHud`, `addHudIcon`, `removeHudIcon`, etc. |

## Tick Rate
HUD renders every 2 ticks (100ms). Only sends boss bar updates when component changes (diff check via `Component.equals`).

## Boss Bar Coexistence
Regular boss bars (dragon health, custom gameplay bars) work alongside HUD layouts. The HUD marker (B=254) is not used by normal text colors, so boss bar titles render correctly.

**Known limitation**: Boss bar text with high-blue colors (aqua §b, blue §9, pink §d) may lose their text shadow due to B×0.25≈63 collision with the shadow detection. The text itself always renders correctly — only the shadow layer is affected.

## Test Command
`/hud` — toggles test HUD layout on the executing player.

## Full Example: Game Mode HUD
```kotlin
val gameHud = hudLayout("game") {
    bar("health") {
        anchor(HudAnchor.BOTTOM_LEFT)
        offset(0.02f, 0.90f)
        sprites(bg = "bar_bg", fill = "bar_fill_red", empty = "bar_empty")
        segments(20)
    }
    text("health_text") {
        anchor(HudAnchor.BOTTOM_LEFT)
        offset(0.25f, 0.90f)
    }
    bar("stamina") {
        anchor(HudAnchor.BOTTOM_LEFT)
        offset(0.02f, 0.86f)
        sprites(bg = "bar_bg", fill = "bar_fill_green", empty = "bar_empty")
        segments(10)
    }
    text("timer") {
        anchor(HudAnchor.TOP_CENTER)
        offset(0f, 0.02f)
    }
    text("kills") {
        anchor(HudAnchor.TOP_RIGHT)
        offset(-0.05f, 0.02f)
    }
    group("effects") {
        anchor(HudAnchor.TOP_LEFT)
        offset(0.02f, 0.05f)
        vertical(spacing = 0.015f)
    }
}

HudManager.register(gameHud)

// On game start:
player.showHud("game")
player.updateHud("health", 20)
player.updateHud("health_text", "100")
player.updateHud("stamina", 10)
player.updateHud("timer", "5:00")
player.updateHud("kills", "0")

// During gameplay:
player.updateHud("health", currentHp)
player.updateHud("health_text", "${currentHp * 5}")
player.updateHud("timer", formatTime(remainingSeconds))
player.updateHud("kills", "$killCount")
player.addHudIcon("effects", "icon_speed")

// On game end:
player.hideHud("game")
```

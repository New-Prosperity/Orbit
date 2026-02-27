# Map-Based Screen

Shader-decoded map-based screen renderer. Encodes true-color pixels into map data using MSB-split encoding, decoded client-side by a GLSL shader for pixel-perfect UIs at 640x384 effective resolution (10x6 map grid, 64x64 true-color pixels per map).

## Usage

```kotlin
import me.nebula.orbit.utils.screen.*

screen(player, Pos(100.0, 65.0, 100.0, -90f, 0f)) {
    cursor {
        item(ItemStack.of(Material.ARROW))
        scale(0.06f)
    }

    background(0xFF1A1A2E.toInt())

    onDraw { canvas ->
        canvas.fill(20, 20, 200, 60, 0xFF0F3460.toInt())
    }

    button("play", 120, 50, 200, 60) {
        onClick { player.sendMessage(Component.text("Play!")) }
        onHover { hovering ->
            Screen.update(player) { canvas ->
                val color = if (hovering) 0xFF1A5276.toInt() else 0xFF0F3460.toInt()
                canvas.fill(20, 20, 200, 60, color)
            }
        }
    }

    sensitivity(1.0)
    escToClose()
    onClose { }
}
```

### With custom config

```kotlin
val config = ScreenConfig(
    eyePos = Pos(100.0, 65.0, 100.0, -90f, 0f),
    canvasWidth = 640,
    canvasHeight = 384,
    fov = 70.0,
    coverage = 0.85,
    sensitivity = 1.0,
)

screen(player, config) {
    cursor { }
    background(0xFF000000.toInt())
    onDraw { canvas -> canvas.fill(0, 0, 640, 384, 0xFFFF0000.toInt()) }
    button("btn", 320, 192, 100, 40) { onClick { } }
}
```

### Extensions

```kotlin
player.openScreen(eyePos) { cursor { }; button(...) { } }
player.closeScreen()
player.hasScreenOpen
```

### Dynamic updates

```kotlin
Screen.update(player) { canvas ->
    canvas.clear(0xFF000000.toInt())
    canvas.fill(10, 10, 100, 50, 0xFFFF0000.toInt())
}
```

### Widget DSL

```kotlin
screen(player, eyePos) {
    cursor { item(ItemStack.of(Material.ARROW)) }
    background(0xFF1A1A2E.toInt())

    panel(20, 20, 600, 344, 0xFF16213E.toInt()) {
        cornerRadius(8)
        label(100, 20, "Main Menu", DEFAULT_FONT, 0xFFFFFFFF.toInt())
        button(200, 80, 200, 50, "Play", DEFAULT_FONT) {
            bgColor(0xFF0F3460.toInt())
            hoverColor(0xFF1A5276.toInt())
            cornerRadius(4)
            onClick { }
        }
        progressBar(200, 160, 200, 20) {
            progress(0.7f)
            fgColor(0xFF4CAF50.toInt())
            bgColor(0xFF333333.toInt())
        }
        image(250, 200, TextureLoader.fromClasspath("textures/logo.png"))
    }

    escToClose()
}
```

Widgets auto-draw, auto-hit-test, and support hover/click. The manual `onDraw` API continues to work alongside widgets (drawn after widgets).

### Textures

```kotlin
val tex = TextureLoader.fromClasspath("textures/icon.png")
val scaled = tex.scaled(32, 32)
canvas.drawTexture(10, 10, scaled)
```

### Drawing primitives

```kotlin
canvas.line(0, 0, 100, 100, 0xFFFFFFFF.toInt())
canvas.circle(50, 50, 30, 0xFF00FF00.toInt())
canvas.filledCircle(50, 50, 30, 0xFF00FF00.toInt())
canvas.roundedRect(10, 10, 200, 100, 8, 0xFF333333.toInt())
canvas.stroke(10, 10, 200, 100, 0xFFFFFFFF.toInt(), 2)
canvas.linearGradient(0, 0, 200, 50, 0xFFFF0000.toInt(), 0xFF0000FF.toInt())
canvas.radialGradient(100, 100, 50, 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
canvas.blendPixel(10, 10, 0x80FF0000.toInt())
```

### Bitmap font

```kotlin
canvas.drawText(DEFAULT_FONT, 10, 10, "Hello World", 0xFFFFFFFF.toInt())
val w = textWidth(DEFAULT_FONT, "Hello")
```

`DEFAULT_FONT` is a built-in 6x8 monospace font (ASCII 32-122, no file dependency). Custom fonts load from a grid-atlas texture:

```kotlin
val font = BitmapFont(atlas = texture, charWidth = 8, charHeight = 12, columns = 16)
```

### Animation

```kotlin
val anim = Screen.animations(player) ?: return
anim.animate(Tween(
    from = 0.0, to = 1.0,
    durationMs = 1000,
    easing = Easing.EASE_IN_OUT,
    interpolator = DoubleInterpolator,
    onUpdate = { value -> /* use value */ },
    onComplete = { /* done */ },
))
```

Drive animations with a repeating tick that calls `Screen.update()`:

```kotlin
val task = MinecraftServer.getSchedulerManager()
    .buildTask {
        Screen.update(player) { canvas -> /* redraw using animated values */ }
        if (!anim.hasActive()) task.cancel()
    }
    .repeat(TaskSchedule.tick(1))
    .schedule()
```

Easing options: `LINEAR`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`. Interpolators: `IntInterpolator`, `DoubleInterpolator`, `ColorInterpolator`, or custom `TweenInterpolator<T>`.

## Architecture

### Performance

- **Single global event listener**: One `EventNode` with O(1) `ConcurrentHashMap` lookup per packet, instead of N per-player EventNodes causing O(N) dispatch per player.
- **Single global remount tick**: One repeating task iterates all sessions, instead of N individual scheduled tasks.
- **Packet-only viewpoint**: Camera target spawned via raw `SpawnEntityPacket` + metadata, no server-side `EntityCreature` ticking/AI/collision overhead per player. Anchor (Camel) remains a real entity for `addPassenger`.
- **Zero-copy tile encoding**: `encodeTileDirect` reads straight from `MapCanvas.pixels`, skipping intermediate `IntArray(4096)` allocation per tile per encode.
- **No-copy previousData**: `MapDisplay` stores `chunk.data` reference directly (encoder allocates fresh each time, never mutates).
- **SIMD-accelerated fill**: `MapCanvas.fill()` uses `java.util.Arrays.fill` (JVM intrinsic vectorized by HotSpot) instead of per-pixel loop.
- **Optimized gradient**: `linearGradient` writes directly to pixel buffer with single dirty mark, instead of N `fill()` calls per column/row.
- **Tile-level dirty tracking**: `MapCanvas` uses a `BitSet` to track which 64x64 tiles have been modified since last `clearDirty()`.
- **Partial encoding**: `MapEncoder.encodeCanvas(canvas, dirtyOnly = true)` skips clean tiles entirely.
- **Row-level partial updates**: `MapDisplay` stores previous tile data and diffs row-by-row, sending only changed row ranges via `ColorContent(columns, rowCount, x, startRow, subData)`.
- **Staggered initial load**: First `open()` batches tile data across multiple ticks (20 tiles per tick) to avoid packet burst.

### Encoding — MSB-Split

Each source pixel (R, G, B) → 4 map bytes in a 2x2 cell:
- `cell[0,0] = (blue  & 0x7F) + 4` — lower 7 bits of blue
- `cell[1,0] = (green & 0x7F) + 4` — lower 7 bits of green
- `cell[0,1] = (red   & 0x7F) + 4` — lower 7 bits of red
- `cell[1,1] = ((R>>7)<<2 | (G>>7)<<1 | (B>>7)) + 4` — packed MSBs

The `+4` offset avoids transparent map color IDs 0-3. Effective resolution: 64x64 true-color pixels per 128x128 map.

### Shader Decode

Client converts each map byte → RGB via hardcoded palette. Shader reverses this:
1. **Detection**: first 2x2 cell = magic signature (4 specific map color IDs)
2. **Reverse lookup**: 128-entry `vec3[]` palette constant, nearest-distance match
3. **Reconstruct**: recombine 7-bit lower + MSB to get original 8-bit channels

Target shaders: `rendertype_text.fsh` (primary, maps use text render type), `entity.fsh` (secondary, belt-and-suspenders).

### Display

Invisible item frame entities in a grid, each holding a `filled_map` with unique map ID. `MapDataPacket` sends encoded pixel data per tile. Multi-layer dirty-checking: content hash skips unchanged tiles, row-level diff minimizes bytes sent for changed tiles.

### Widget System

Composable widget tree with absolute positioning relative to parent. Widgets auto-draw (before `onDraw` callback) and auto-hit-test for hover/click. Available widgets: `Panel`, `Label`, `Button`, `ProgressBar`, `ImageWidget`. Build via DSL on `ScreenBuilder` or `PanelBuilder` for nesting.

## Coordinate System

- Canvas origin `(0, 0)` is top-left
- Default: 640x384 pixel grid (10x6 map tiles)
- Manual button positions are center-based with width/height for hit area
- Widget positions are top-left relative to parent widget
- Cursor position clamped to canvas bounds
- Screen must face a cardinal direction (yaw auto-snapped to nearest 90°)

## Config Parameters

| Parameter | Default | Constraints | Description |
|---|---|---|---|
| `eyePos` | required | — | Camera position and yaw/pitch (yaw snapped to cardinal) |
| `canvasWidth` | `640` | >0, multiple of 64 | Pixel width |
| `canvasHeight` | `384` | >0, multiple of 64 | Pixel height |
| `fov` | `70.0` | 30.0–120.0 | Minecraft vertical FOV in degrees |
| `coverage` | `0.85` | 0.1–1.0 | Fraction of FOV the screen covers |
| `sensitivity` | `1.0` | — | Mouse sensitivity multiplier |

Depth is auto-computed: `(tilesY / 2) / tan(fov * coverage / 2)`.

## API

| Function | Description |
|---|---|
| `screen(player, eyePos) { }` | Build and open a screen |
| `screen(player, config) { }` | Open with custom ScreenConfig |
| `Screen.close(player)` | Close and restore player state |
| `Screen.update(player) { canvas -> }` | Redraw canvas and send deltas |
| `Screen.isOpen(player)` | Check if player has active screen |
| `Screen.closeAll()` | Close all active sessions |
| `Screen.animations(player)` | Get AnimationController for session |

## MapCanvas API

| Method | Description |
|---|---|
| `pixel(x, y, color)` | Set single pixel (ARGB packed int) |
| `get(x, y)` | Get pixel color at position |
| `clear(color)` | Fill entire canvas |
| `fill(x, y, w, h, color)` | Fill rectangle |
| `drawImage(x, y, src, w, h)` | Draw IntArray image (skip transparent) |
| `drawTexture(x, y, texture)` | Draw Texture (extension) |
| `getRegion(x, y, w, h)` | Extract pixel region |
| `isDirty(tileX, tileY)` | Check if tile was modified |
| `clearDirty()` | Reset all dirty flags |
| `markAllDirty()` | Mark all tiles dirty |

## Drawing Primitives (extensions on MapCanvas)

| Method | Description |
|---|---|
| `line(x0, y0, x1, y1, color)` | Bresenham line |
| `circle(cx, cy, r, color)` | Midpoint circle outline |
| `filledCircle(cx, cy, r, color)` | Filled circle |
| `roundedRect(x, y, w, h, r, color)` | Rounded rectangle |
| `stroke(x, y, w, h, color, thickness)` | Rectangle outline |
| `linearGradient(x, y, w, h, from, to, horizontal)` | Linear gradient fill |
| `radialGradient(cx, cy, r, inner, outer)` | Radial gradient fill |
| `blendPixel(x, y, color)` | Alpha-blended pixel |

## Texture API

| Method | Description |
|---|---|
| `TextureLoader.fromClasspath(path)` | Load from classpath resource |
| `TextureLoader.fromBytes(bytes)` | Load from raw bytes |
| `TextureLoader.fromBufferedImage(img)` | Load from BufferedImage |
| `Texture.scaled(w, h)` | Nearest-neighbor scale |
| `Texture.subRegion(x, y, w, h)` | Extract sub-region |

## Font API

| Method | Description |
|---|---|
| `canvas.drawText(font, x, y, text, color)` | Render text with tint color |
| `textWidth(font, text)` | Calculate text pixel width |
| `DEFAULT_FONT` | Built-in 6x8 monospace (ASCII 32-122) |

## Widget API

| Widget | Properties | Description |
|---|---|---|
| `Panel` | color, cornerRadius | Container with optional rounded corners |
| `Label` | text, font, color | Text display |
| `Button` | text, font, bgColor, hoverColor, textColor, cornerRadius, onClick | Interactive button with hover effect |
| `ProgressBar` | progress (0-1), fgColor, bgColor, cornerRadius | Progress indicator |
| `ImageWidget` | texture | Texture display |

Widgets support: `visible`, `parent`/`addChild()` nesting, absolute `x`/`y` relative to parent, `hitTest()`, `onHover()`, `onClick()`.

## File Structure

```
utils/screen/
├── Screen.kt              (session management, camera/input/cursor)
├── ScreenConfig.kt        (config, projection math, screen basis)
├── ScreenBuilder.kt       (DSL builder + widget DSL)
├── ScreenTestCommand.kt   (test command)
├── canvas/
│   ├── MapCanvas.kt       (pixel buffer + dirty tracking)
│   └── CanvasDrawing.kt   (drawing primitive extensions)
├── encoder/
│   └── MapEncoder.kt      (MSB-split encoding + partial encode)
├── display/
│   └── MapDisplay.kt      (item frame grid + row-level partial updates)
├── shader/
│   └── MapShaderPack.kt   (GLSL palette generation, shader entries)
├── texture/
│   └── TextureLoader.kt   (image loading, Texture data class)
├── font/
│   └── BitmapFont.kt      (grid-atlas font, drawText, built-in 6x8)
├── animation/
│   └── Tween.kt           (tween system, easing, AnimationController)
├── widget/
│   ├── Widget.kt          (abstract widget base, hit testing)
│   └── Widgets.kt         (Panel, Label, Button, ProgressBar, ImageWidget)
└── screen.md              (this file)
```

## Internals

- Packet-based: item frames use negative IDs from `AtomicInteger(-6_000_000)`, cursor from `-5_000_000`, viewpoint from `-7_000_000`, map IDs from `Int.MAX_VALUE` downward
- Packet-only Villager viewpoint (client-side only, no server entity) for camera lock via `CameraPacket`
- Invisible camel anchor (real `EntityCreature`) for mouse input capture via `addPassenger`
- Single global `EventNode("screen-global")` with O(1) session lookup, single global remount task
- ITEM_DISPLAY cursor with interpolation
- Delta-based cursor movement, first-packet calibration
- Multi-level dirty-checking: tile-level BitSet → content hash → row-level diff
- Row-level partial MapDataPacket: only changed rows sent via `ColorContent` with row offset
- Staggered initial load: 20 tiles per tick across multiple scheduler tasks
- Magic signature at first tile's (0,0) cell for shader detection
- Widget tree drawn before `onDraw` callback, hit-tested alongside manual buttons
- AnimationController per session, ticked on every `Screen.update()` call
- Disconnect cleanup via `PlayerDisconnectEvent`
- Without resource pack: maps show garbled palette colors (expected graceful degradation)

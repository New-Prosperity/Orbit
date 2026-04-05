# World Editing Stack

FAWE-inspired world editing system with chunk-batched operations, compressed delta history, and section-level palette manipulation.

## Architecture

- **ChunkBatchQueue** — accumulates changes per-chunk in `IntArray[4096]` per section. Flushes via `palette.set()` + `chunk.invalidate()` + `chunk.sendChunk()`.
- **ChangeSet** — compressed zigzag-varint delta history (~5 bytes/change). Undo/redo support.
- **EditSession** — per-operation disposable with queue + changeset + block limit.
- **PlayerEditSession** — per-player selection, clipboard, 50-entry undo/redo stack.
- **Mask** — ordinal-based block predicates (`block`, `not`, `existing`, `solid`, `liquid`, string parsing).
- **Pattern** — block sources (`single`, `random`, `gradient`, `noise`, string parsing).
- **Filter** — section-level batch iteration (set, replace, count).
- **Brush** — interactive tools (sphere, cylinder, smooth, raise, lower, erode, fill, paste).

## Commands

All commands use `//` prefix (e.g., `//set stone`).

| Command | Description |
|---------|-------------|
| `//wand` | Give selection wand (wooden axe) |
| `//pos1` / `//pos2` | Set selection to current position |
| `//set <pattern>` | Fill selection |
| `//replace <mask> <pattern>` | Replace matching blocks |
| `//copy` / `//cut` | Copy/cut to clipboard |
| `//paste` | Paste clipboard |
| `//undo` / `//redo` | Undo/redo last operation |
| `//walls` / `//outline <pattern>` | Shell operations |
| `//drain` | Remove liquids |
| `//smooth [iterations]` | Smooth terrain |
| `//naturalize` | Grass → dirt → stone layers |
| `//sphere` / `//hsphere <pattern> <radius>` | Create sphere |
| `//size` | Selection dimensions |

Wand: left-click = pos1, right-click = pos2. Particle preview on selection edges.

## Pattern Syntax

- `stone` — single block
- `50%stone,50%granite` — weighted random

## Mask Syntax

- `stone` — match block
- `!air` — negate
- `#existing` — non-air
- `#solid` / `#liquid` — property check

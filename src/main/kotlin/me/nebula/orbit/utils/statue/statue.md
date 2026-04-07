# Statue Utility

Animated player statues with skins, cosmetics, holograms, and click interaction for the hub. Uses a single reusable `player_statue` blueprint registered once at startup. Per-statue skins are resolved from the player's GameProfile UUID via Mojang skin servers. Falls back to NPC (packet-based fake player) when ModelEngine is unavailable.

## Architecture

### Single Blueprint Design
- ONE model (`player_statue`) added to the resource pack at startup, never changes per player
- Unlimited statues with different skins, zero additional pack size
- Client resolves skin from GameProfile UUID via Mojang skin servers
- Animations defined in the blueprint, played per-statue instance

### Rendering Flow (ModelEngine)
1. `PlayerModelGenerator.registerBlueprint()` called once at startup
2. On statue spawn, a hidden skin NPC is created with the player's GameProfile (UUID + skin textures)
3. Client sees the NPC's UUID, downloads skin from Mojang
4. A `StandaloneModelOwner` is created using the shared `player_statue` blueprint
5. Idle animation auto-plays

### Fallback (NPC)
If blueprint registration fails or ModelEngine is unavailable, falls back to packet-based fake player with skin textures, equipment rendering, and head tracking.

## Components

### `StatueManager` (object)
Core lifecycle manager for all statues.

- `install()` / `uninstall()` -- register blueprint + start/stop tick task + load saved configs
- `spawn(id, config)` -- create a statue, returns `ActiveStatue?`
- `remove(id)` -- destroy a statue by ID (including pet/companion)
- `removeAll()` -- destroy all statues
- `get(id)` / `all()` -- query statues
- `moveStatue(id, newPos)` -- move a statue to a new position (remove + respawn)
- `setAnimation(id, animationName)` -- set a specific animation on a ModelEngine statue
- `setLeaderboardConfig(source, period)` -- change leaderboard source/period for auto-managed statues
- `refreshTopPlayerStatues(instance, podiums)` -- smooth refresh: only remove/respawn if player changed for a position
- `installAutoRefresh(instance, podiums)` -- auto-refresh top player statues every 5 minutes

### Persistence
- `saveConfigs()` -- writes non-auto-managed statues to `statues.json` via `Orbit.resources`
- `loadConfigs()` -- reads `statues.json`, spawns each saved statue on install
- Uses `GsonProvider.pretty` for writing, `GsonProvider.default` for reading
- `SavedStatueConfig` data class with all serializable fields

### `StatueConfig` (data class)
```kotlin
StatueConfig(
    playerUuid = uuid,
    position = Pos(10.0, 65.0, 0.0),
    instance = hubInstance,
    rotationSpeed = 1f,     // 0 = static, >0 = rotating (NPC only)
    showCosmetics = true,
    showHologram = true,
    label = "#1 Player",
    leaderboardSource = "rating:battleroyale",
    leaderboardPeriod = "ALL_TIME",
    autoManaged = false,
)
```

### `ActiveStatue` (data class)
Holds the NPC (nullable), ModelEngine `StandaloneModelOwner` (nullable), hologram, pet/companion references, cosmetic cache, and config for a spawned statue. Exactly one of `npc` or `modelOwner` is non-null.

### `StatuePodium` (data class)
Position + rank for automatic top-player statues.

### `PlayerModelGenerator` (object)
Single reusable player model blueprint generator. Creates ONE `BlockbenchModel` with exact Minecraft player proportions, standard 64x64 skin UV mapping (including overlay layers), and bone hierarchy for animations. Supports file-first loading: if `models/player_statue.bbmodel` exists in the data directory, loads from file; otherwise generates programmatically and auto-exports the `.bbmodel` for designer editing in Blockbench.

- `BLUEPRINT_NAME` -- constant `"player_statue"`
- `registerBlueprint()` -- loads from `models/player_statue.bbmodel` if present, falls back to programmatic generation + auto-exports the file. No-op if already registered.
- `isRegistered()` -- checks if the blueprint exists in ModelEngine
- `exportBbmodelJson(model)` -- serializes a `BlockbenchModel` into standard Blockbench JSON format, round-trip compatible with `BlockbenchParser.parse()`
- `exportToFile(outputPath)` -- builds the player model and writes the `.bbmodel` JSON to the given path

#### File-First Loading Flow
1. First boot: generates programmatically + auto-exports `models/player_statue.bbmodel`
2. Designer opens exported file in Blockbench, edits animations/poses
3. Designer puts modified file back in `models/`
4. Next boot: loads from file -- designer's changes are used
5. If file is corrupted/deleted: falls back to programmatic generation + re-exports

#### Player Model Geometry
All measurements in Blockbench pixels (1 pixel = 1/16 of a block):

```
root (pivot: 0, 0, 0)
+-- head (pivot: 0, 24, 0)
|   +-- head_cube: (-4, 24, -4) to (4, 32, 4)
|   +-- hat_cube: (-4.5, 23.5, -4.5) to (4.5, 32.5, 4.5)  [overlay]
+-- body (pivot: 0, 24, 0)
|   +-- body_cube: (-4, 12, -2) to (4, 24, 2)
|   +-- jacket_cube: (-4.5, 11.5, -2.5) to (4.5, 24.5, 2.5)  [overlay]
+-- right_arm (pivot: -5, 22, 0)
|   +-- right_arm_cube: (-8, 12, -2) to (-4, 24, 2)
|   +-- right_sleeve_cube: (-8.5, 11.5, -2.5) to (-3.5, 24.5, 2.5)
+-- left_arm (pivot: 5, 22, 0)
|   +-- left_arm_cube: (4, 12, -2) to (8, 24, 2)
|   +-- left_sleeve_cube: (3.5, 11.5, -2.5) to (8.5, 24.5, 2.5)
+-- right_leg (pivot: -2, 12, 0)
|   +-- right_leg_cube: (-4, 0, -2) to (0, 12, 2)
|   +-- right_pants_cube: (-4.5, -0.5, -2.5) to (0.5, 12.5, 2.5)
+-- left_leg (pivot: 2, 12, 0)
    +-- left_leg_cube: (0, 0, -2) to (4, 12, 2)
    +-- left_pants_cube: (-0.5, -0.5, -2.5) to (4.5, 12.5, 2.5)
```

#### UV Mapping
Standard Minecraft 64x64 skin layout. Each body part maps to the canonical skin UV regions:
- Base layers: head, body, right arm, left arm, right leg, left leg
- Overlay layers: hat, jacket, right sleeve, left sleeve, right pants, left pants

#### Built-in Animations
| Name | Type | Description |
|------|------|-------------|
| `idle` | Loop (2s) | Subtle body sway (body X +/-2 deg) + head nod (head X +/-1 deg) |
| `wave` | Once (1s) | Right arm swings up -120 deg and back down |
| `crossed_arms` | Hold (static) | Both arms rotated -30 deg X, +/-20 deg Y to cross in front |
| `celebrate` | Once (1.5s) | Both arms raised up -150 deg X, body Y offset +2px for 1s |
| `salute` | Once (1.5s) | Right arm raised to head -45 deg X, holds for 1s |
| `look_around` | Loop (4s) | Head rotates: center -> left 40 deg -> pause -> right 40 deg -> pause -> center |
| `sit` | Hold (static) | Both legs rotate -90 deg X, body origin shifts down -6px |

#### Skin Resolution
No skin download or per-player model generation needed. The client resolves the player's skin from the GameProfile UUID attached to the FakePlayer NPC entity. Mojang skin servers handle texture delivery.

### `StatueProfileMenu` (object)
Opens a 6-row GUI showing the featured player's profile (rank, level, stats, cosmetics, ratings).

- `open(viewer, targetUuid, targetName)` -- async data load, then open GUI

### `StatueCommand`
Staff command `/statue` (permission: `orbit.statue`).

```
/statue add <id> <player> [label]         -- place statue at your position
/statue remove <id>                       -- remove a statue
/statue list                              -- list all statues
/statue refresh                           -- reload all statue data
/statue move <id> <x> <y> <z>            -- move statue to new position
/statue pose <id> <animation>             -- set statue animation (idle/wave/crossed_arms/celebrate/salute/look_around/sit)
/statue leaderboard <source> <period>     -- change leaderboard for auto statues
```

## Features

- **Persistence**: Non-auto-managed statues saved to `statues.json`, auto-loaded on install
- **Player skin**: resolved from GameProfile UUID via Mojang skin servers (both backends)
- **Custom armor**: equipped armor cosmetics rendered on the NPC (NPC backend only)
- **Hologram**: rank, name, level, best rating tier displayed above (both backends)
- **Aura particles**: if the featured player has an aura equipped, particles spawn every 5 ticks
- **Trail particles**: if equipped, trail particles spawn at the statue's feet every 20 ticks
- **Effect particles**: kill/win/spawn effects play every 100 ticks (5 seconds) around the statue
- **Pet cosmetics**: if the featured player has a pet equipped, spawns a pet entity near the statue
- **Companion cosmetics**: if equipped, spawns a companion model orbiting the statue position
- **Head tracking**: NPC looks at nearby players (NPC backend only)
- **Rotation**: configurable rotation speed for display-style statues (NPC backend only)
- **Click interaction**: right-click plays sound + wave animation (ModelEngine) + opens `StatueProfileMenu`
- **Animations**: idle breathing, wave, crossed arms, celebrate, salute, look around, sit (ModelEngine backend)
- **Top player auto-statues**: query `RankingStore` or `RatingStore` for top N players
- **Smooth refresh**: only remove/respawn leaderboard statues if the player changed for a position
- **Rank particles**: podium statues (#1/#2/#3) show particle rings at feet every 10 ticks
- **Distance culling**: skips expensive tick operations if no player is within 64 blocks
- **Cosmetic caching**: 30-second TTL cache per statue instead of loading every 5 ticks
- **Ban check**: skips spawning statues for players with active bans
- **Configurable leaderboard**: `leaderboardSource` and `leaderboardPeriod` on `StatueConfig`

## Usage

### Manual statues (command)
```
/statue add staff1 Notch "Staff Pick"
/statue remove staff1
/statue move staff1 10 65 0
/statue pose staff1 celebrate
```

### Automatic top-player statues (code)
```kotlin
StatueManager.installAutoRefresh(hubInstance, listOf(
    StatuePodium(Pos(10.0, 65.0, 0.0), 1),
    StatuePodium(Pos(15.0, 65.0, 0.0), 2),
    StatuePodium(Pos(20.0, 65.0, 0.0), 3),
))
```

### Programmatic statues
```kotlin
StatueManager.spawn("my_statue", StatueConfig(
    playerUuid = uuid,
    position = Pos(0.0, 65.0, 0.0),
    instance = instance,
    label = "Featured",
))
```

### Direct model usage (without StatueManager)
```kotlin
PlayerModelGenerator.registerBlueprint()
val owner = standAloneModel(position) {
    model(PlayerModelGenerator.BLUEPRINT_NAME) {
        animation("wave", lerpIn = 0.2f)
    }
}
owner.show(player)
```

## Wiring

- `HubMode.install()` calls `StatueManager.install()` (which registers the blueprint + starts tick task + loads saved configs)
- `HubMode.shutdown()` calls `StatueManager.uninstall()`
- `/statue` command registered in `Orbit.kt`

## Translation Keys

| Key | Placeholders |
|-----|-------------|
| `orbit.statue.spawned` | `{id}`, `{player}` |
| `orbit.statue.removed` | `{id}` |
| `orbit.statue.not_found` | `{id}` |
| `orbit.statue.already_exists` | `{id}` |
| `orbit.statue.list_empty` | -- |
| `orbit.statue.refreshing` | -- |
| `orbit.statue.profile_title` | `{player}` |
| `orbit.statue.level_label` | `{level}` |
| `orbit.statue.total_wins` | `{wins}` |
| `orbit.statue.total_kills` | `{kills}` |
| `orbit.statue.total_games` | `{games}` |
| `orbit.statue.win_rate` | `{rate}` |
| `orbit.statue.rating_gamemode` | `{gamemode}` |
| `orbit.statue.peak_rating` | `{rating}` |
| `orbit.statue.games_played` | `{games}` |
| `orbit.statue.label_top` | `{rank}` |
| `orbit.statue.click_hint` | -- |
| `orbit.statue.moved` | `{id}` |
| `orbit.statue.pose_set` | `{id}`, `{pose}` |
| `orbit.statue.leaderboard_set` | `{source}`, `{period}` |
| `orbit.statue.configs_loaded` | `{count}` |

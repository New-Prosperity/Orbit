# Vanish

Rank-aware packet-level player hiding. Vanished players are invisible to non-staff via `DestroyEntitiesPacket` + `PlayerInfoRemovePacket`. State is stored on the Player via `Tag.Boolean("nebula:vanished")` and persisted cross-server via Hazelcast `ReplicatedMap<UUID, Boolean>("vanished-players")`.

## Visibility Rules

- Non-vanished targets are always visible.
- Players see themselves even when vanished.
- Viewers need `staff.vanish.see` permission to see any vanished player.
- Among staff, lower `rankWeight` (higher rank) can see equal or higher weight vanished players.

## VanishManager

| Method | Description |
|---|---|
| `canSee(viewer, target)` | Rank-aware visibility check |
| `vanish(player)` | Hide from non-permitted players, persist to replicated map |
| `unvanish(player)` | Nick-aware reveal: sends nicked identity if player has active nick, real identity otherwise |
| `isVanished(player: Player)` | Check via player tag |
| `isVanished(uuid: UUID)` | Check via replicated map |
| `toggle(player)` | Toggle vanish, returns `true` if now vanished |
| `visiblePlayerCount(viewer?)` | Count of players visible to viewer (or non-vanished count if null) |
| `gameParticipantCheck` | Lambda `(Player) -> Boolean`, returns `true` if player is an active game participant (prevents vanishing in-game). Default `{ false }`. Game modes set this on install. |
| `installListeners()` | Register spawn/disconnect/damage/pickup event listeners |

## Collision Prevention

- **Damage**: `EntityDamageEvent` from `EntityDamage` source is cancelled if the target is vanished and the attacker cannot see them.
- **Item pickup**: `PickupItemEvent` is cancelled for vanished players to prevent revealing their position.

## Integrations

- **Nick system** (`NickManager`) — `applyNick`, `removeNick`, and instance-change nick broadcast all skip players who cannot see a vanished target. `unvanish` sends nicked identity instead of real identity when the player has an active nick.
- **Tab completion** (`CommandHelpers.suggestPlayers`, `resolvePlayer`) — vanished players hidden from non-permitted viewers
- **Tab list** (`HubMode.buildTabEntries`) — vanished players filtered from tab entries and player count header
- **Nameplates** (`NameplateManager.showTo`, `install`) — nameplate not shown if viewer cannot see target
- **Game participant check** — `VanishCommand` blocks self-vanish when `gameParticipantCheck` returns `true`

## Extension Functions

| Function | Description |
|---|---|
| `player.isVanished` | Property shortcut for `VanishManager.isVanished` |
| `player.canSee(other)` | Shortcut for `VanishManager.canSee` |
| `player.vanish()` | Shortcut for `VanishManager.vanish` |
| `player.unvanish()` | Shortcut for `VanishManager.unvanish` |
| `player.toggleVanish()` | Shortcut for `VanishManager.toggle` |

## Vanish Command

`/vanish` — permission `staff.vanish`. Toggles self vanish. Blocked when `gameParticipantCheck` returns `true` (replies `orbit.vanish.in_game`). With `<player>` argument requires `staff.vanish.others`.

## Event Listener Order

In `Orbit.kt`, listeners are installed in order: `PlayerCache` -> `NickManager` -> `VanishManager`. VanishManager runs last so its `PlayerSpawnEvent` handler hides what NickManager's handler showed.

## Example

```kotlin
VanishManager.installListeners()

player.vanish()

if (player.isVanished) {
    // vanished
}

player.unvanish()

val nowVanished = player.toggleVanish()
val count = VanishManager.visiblePlayerCount(viewer)

// Game modes can set the participant check:
VanishManager.gameParticipantCheck = { tracker.isAlive(it.uuid) }
```

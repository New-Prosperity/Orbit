# LeaderboardDisplay

Query and display rankings from Gravity's `RankingStore`. Supports chat output, paginated GUI, category selector, and persistent text display entities for hub worlds.

## API

### DSL

```kotlin
val lb = leaderboardDisplay {
    column("br_kills", "orbit.leaderboard.kills", Material.DIAMOND_SWORD)
    column("br_wins", "orbit.leaderboard.wins", Material.GOLD_INGOT)
    defaultPeriod(Periodicity.ALL_TIME)
}
```

### Chat

```kotlin
lb.sendChat(player, "br_kills")
lb.sendChat(player, "br_kills", Periodicity.WEEKLY, limit = 5)
```

### GUI

```kotlin
lb.openCategorySelector(player)
lb.openGui(player, "br_kills")
lb.openGui(player, "br_kills", Periodicity.DAILY)
```

### Text Display (Hub)

Packet-based per-player text display with a server-side INTERACTION entity for click detection. Each player sees their own periodicity/view state independently.

```kotlin
val textLb = leaderboardTextDisplay(instance) {
    display(lb)
    position(Pos(0.0, 65.0, 0.0))
    title("Battle Royale Kills")
    statKey("br_kills")
    refreshSeconds(30)
    entriesShown(10)
    scale(1.0f)
}
textLb.spawn()
textLb.despawn()
```

- Left-click INTERACTION entity: cycles periodicity (ALL_TIME → DAILY → WEEKLY → MONTHLY → …)
- Right-click INTERACTION entity: toggles between Top 10 and personal rank view
- TEXT_DISPLAY is a virtual packet entity — per-player rendering, no instance entity
- INTERACTION is the only server-side entity — lightweight, invisible, handles click events
- Auto-shows to players joining the instance, auto-refreshes on configured interval

### Player Rank

```kotlin
val rank: RankedPlayer? = lb.playerRank(player.uuid, "br_kills")
```

### Translation Keys

- `orbit.leaderboard.header` — `<stat>`, `<period>`
- `orbit.leaderboard.empty`
- `orbit.leaderboard.title`
- `orbit.leaderboard.personal_rank`

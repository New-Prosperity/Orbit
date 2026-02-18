# Map Pool

DSL for defining map pools with selection strategies (random, rotation, vote), player-count filtering, and recent-map exclusion.

## Key Classes

- **`MapPool`** -- manages a collection of maps with selection logic
- **`GameMap`** -- data class with name, display name, authors, player limits, metadata
- **`SelectionStrategy`** -- enum: `RANDOM`, `ROTATION`, `VOTE`
- **`MapPoolBuilder`** / **`GameMapBuilder`** -- DSL builders

## Usage

### Create

```kotlin
val pool = mapPool("skywars") {
    strategy(SelectionStrategy.VOTE)
    recentExclusion(2)

    map("castle", "Castle Wars") {
        authors("Alice", "Bob")
        minPlayers(4)
        maxPlayers(16)
        meta("theme", "medieval")
    }
    map("sky-island", "Sky Island")
}
```

### Selection

```kotlin
val nextMap = pool.selectNext(playerCount = 8)
```

### Voting

```kotlin
pool.vote(player.uuid, "castle")
pool.removeVote(player.uuid)
pool.voteCount("castle")
pool.voteTally()
pool.clearVotes()
```

### Queries

```kotlin
pool.maps
pool.size
pool.getMap("castle")
pool.eligible(playerCount = 8)
```

Recently played maps (configurable count) are excluded from selection. When using `VOTE` strategy, ties fall back to random among top-voted eligible maps.

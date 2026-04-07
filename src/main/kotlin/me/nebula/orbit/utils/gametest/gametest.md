# GameTest

In-game minigame testing framework. Tests run on the live server via `/gametest`, spawning fake players, simulating game scenarios, and reporting results to the operator in chat.

## Architecture

| File | Purpose |
|---|---|
| `GameTest.kt` | DSL, registry, definition, builder, fixtures |
| `GameTestContext.kt` | Runtime context with assertions, player simulation, event assertions, metrics |
| `GameTestAssertions.kt` | Fluent infix assertion DSL (`shouldBe`, `shouldContain`, etc.) |
| `GameTestEdgeCases.kt` | Edge case testing utilities: concurrency, timing, combat, game flow, inventory |
| `GameTestRunner.kt` | Orchestration, lifecycle, virtual thread execution, live session management |
| `GameTestCommand.kt` | `/gametest` command |
| `LiveTestSession.kt` | Live test session: attaches to running game, spawns/manages bots dynamically |
| `EventRecorder.kt` | Event capture system for recording and asserting on events during tests |

## DSL

```kotlin
gameTest("elimination-last-alive-wins") {
    description = "Last player alive should win the game"
    playerCount = 4
    timeout = 30.seconds

    withGameMode { SomeGameMode(settings) }

    setup {
        waitForPhase(GamePhase.PLAYING)

        players[0].simulateKill()
        waitTicks(5)
        players[1].simulateKill()
        waitTicks(5)
        players[2].simulateKill()
        waitTicks(10)

        waitForPhase(GamePhase.ENDING)
        assertAlive(players[3])
        assertAliveCount(1)
    }
}
```

`gameTest()` auto-registers the definition in `GameTestRegistry`.

### Live Test DSL

```kotlin
gameTest("stress-test-20") {
    description = "Stress test with 20 bots in live game"
    playerCount = 20
    live = true

    setup {
        waitTicks(100)
        log("${tracker?.aliveCount} players alive after 5 seconds")
        assert(tracker?.aliveCount ?: 0 > 5, "At least 5 should survive")
    }
}
```

When `live = true`, the runner does not create a new instance or install a new GameMode. It uses the operator's current game. `playerCount` means "number of bots to spawn into the live game".

## GameTestBuilder

| Property | Default | Description |
|---|---|---|
| `description` | `""` | Human-readable test description |
| `playerCount` | `2` | Number of fake players to spawn |
| `timeout` | `30.seconds` | Max duration before auto-fail |
| `live` | `false` | Use current game instead of creating new instance |
| `tags` | `emptyList()` | Tags for grouping/filtering tests |

| Method | Description |
|---|---|
| `withGameMode { }` | Factory for GameMode to install on test instance |
| `live()` | Shorthand for `live = true` |
| `useFixture(fixture)` | Apply a `GameTestFixture`'s defaults (gameModeFactory, playerCount, timeout) |
| `beforeEach { }` | Hook that runs before the setup block (receives `GameTestContext`) |
| `afterEach { }` | Hook that runs after setup, even on failure (receives `GameTestContext`) |
| `setup { }` | Test body (runs in virtual thread) |

## GameTestContext

### Tick Control

| Method | Description |
|---|---|
| `waitTicks(n)` | Sleep for N game ticks (N * 50ms) |
| `waitUntil(timeout) { condition }` | Poll until condition is true |
| `waitForPhase(phase, timeout)` | Wait for GameMode to reach a specific phase |

### Player Simulation (extension functions on Player)

**Combat / Core:**

| Method | Description |
|---|---|
| `player.simulateAttack(target)` | Deal PLAYER_ATTACK damage to target |
| `player.simulateMove(pos)` | Teleport player to position |
| `player.simulateUseItem()` | Swing main hand |
| `player.simulateBreakBlock(pos)` | Set block at position to AIR |
| `player.simulatePlaceBlock(pos, block)` | Set block at position |
| `player.simulateDamage(amount, type)` | Apply damage of given type |
| `player.simulateChat(message)` | Send chat message |
| `player.simulateKill()` | Trigger death via GameMode.handleDeath or raw damage |

**Movement / State:**

| Method | Description |
|---|---|
| `player.simulateSneak(sneaking)` | Set sneaking state (default true) |
| `player.simulateSprint(sprinting)` | Set sprinting state (default true) |
| `player.simulateJump()` | Apply upward velocity impulse (8.0) |
| `player.simulateLookAt(target: Point)` | Calculate yaw/pitch to face a point, call setView |
| `player.simulateLookAt(entity: Entity)` | Face an entity's eye position |
| `player.simulateRespawn()` | Teleport to respawnPoint |

**Inventory / Equipment:**

| Method | Description |
|---|---|
| `player.simulateHeldSlot(slot)` | Switch held item slot (0-8) |
| `player.simulateEquip(slot, item)` | Set equipment in an EquipmentSlot |
| `player.simulateGiveItem(item)` | Add item to inventory |
| `player.simulateDropItem()` | Remove held item and spawn as ItemEntity |
| `player.simulateSwapHands()` | Swap main hand and off hand items |
| `player.simulateClearInventory()` | Clear entire inventory |

**Block Interactions:**

| Method | Description |
|---|---|
| `player.simulateInteractBlock(pos)` | Fire PlayerBlockInteractEvent for block at pos |
| `player.simulateOpenContainer(pos)` | Invoke block handler's onInteract to open container |

**Effects:**

| Method | Description |
|---|---|
| `player.simulateAddEffect(effect, duration, amplifier)` | Add a potion effect (amplifier default 0) |
| `player.simulateRemoveEffect(effect)` | Remove a specific potion effect |
| `player.simulateClearEffects()` | Remove all active effects |

**Health / Food:**

| Method | Description |
|---|---|
| `player.simulateSetHealth(health)` | Set player health directly |
| `player.simulateSetFood(food, saturation)` | Set food level and saturation (saturation default 5.0) |
| `player.simulateHeal(amount)` | Heal by amount, capped at max health |

**Projectiles:**

| Method | Description |
|---|---|
| `player.simulateShootProjectile(type, speed)` | Spawn projectile entity in look direction (speed default 1.5) |

**Utility:**

| Method | Description |
|---|---|
| `player.simulateCommand(command)` | Execute a command as the player via CommandManager |

### Live Mode Methods

| Method | Description |
|---|---|
| `spawnBots(count)` | Spawn additional bots into the live game (live mode only) |
| `removeBots()` | Remove all spawned bots (live mode only) |
| `isLive` | Whether this context is a live session |
| `liveSession` | The `LiveTestSession` (null if not live) |

### Assertions (throw GameTestFailure on failure)

**General:**

| Method | Description |
|---|---|
| `assert(condition, message)` | Generic boolean assertion |
| `assertEqual(expected, actual, message)` | Equality check |
| `assertPhase(expected)` | Check current game phase |

**Tracker / Game State:**

| Method | Description |
|---|---|
| `assertAlive(player)` | Player must be alive |
| `assertDead(player)` | Player must not be alive |
| `assertAliveCount(n)` | Exact alive player count |
| `assertAliveCountAtLeast(min)` | Alive count >= min |
| `assertAliveCountAtMost(max)` | Alive count <= max |
| `assertScore(player, expected)` | Player score check |
| `assertKills(player, expected)` | Kill count check |
| `assertDeaths(player, expected)` | Death count check |
| `assertStreak(player, expected)` | Kill streak check |
| `assertTeam(player, expected)` | Team assignment check |
| `assertTeamAlive(team, expected)` | Alive count for a team |
| `assertTeamEliminated(team)` | Team has no alive members |
| `assertPlacement(player, expected)` | Elimination placement check |
| `assertWinner(player)` | Player has placement 1 |

**Position / Location:**

| Method | Description |
|---|---|
| `assertPosition(player, expected, tolerance)` | Player within tolerance of position (default 0.5) |
| `assertNear(player, target, radius)` | Player within radius of point |
| `assertInInstance(player, instance)` | Player is in the given instance |
| `assertOnGround(player)` | Player is on the ground |

**Inventory:**

| Method | Description |
|---|---|
| `assertHasItem(player, material)` | Any slot contains material |
| `assertHasItemCount(player, material, count)` | Total count of material in inventory |
| `assertItemInSlot(player, slot, material)` | Specific slot contains material |
| `assertEmptyInventory(player)` | All slots are air |
| `assertHeldItem(player, material)` | Currently held item matches material |

**Health / Status:**

| Method | Description |
|---|---|
| `assertHealth(player, expected, tolerance)` | HP check (default tolerance 0.01) |
| `assertHealthAbove(player, min)` | HP > min |
| `assertHealthBelow(player, max)` | HP < max |
| `assertFood(player, expected)` | Food level check |
| `assertHasEffect(player, effect)` | Has active potion effect |
| `assertNoEffect(player, effect)` | Does not have potion effect |

**Blocks:**

| Method | Description |
|---|---|
| `assertBlock(pos, expected)` | Block at position matches (uses `block.compare()`) |
| `assertBlockMaterial(pos, expected)` | Block material matches |
| `assertAir(pos)` | Block is air |

**Entities:**

| Method | Description |
|---|---|
| `assertEntityCount(type, expected)` | Entity count in test instance |
| `assertEntityNear(pos, radius, type, minCount)` | Entities of type near point (default minCount 1) |

**Temporal:**

| Method | Description |
|---|---|
| `assertWithinTicks(ticks) { block }` | Block must complete within N ticks |
| `assertEventually(timeout) { condition }` | Condition must become true within timeout (default 5s) |

### Event Assertions

Requires `events.record<EventType>()` to be called first to register a capture.

| Method | Description |
|---|---|
| `assertEventFired<E>(message)` | Assert that event type E was fired at least once |
| `assertEventNotFired<E>(message)` | Assert that event type E was never fired |
| `assertEventCount<E>(expected, message)` | Assert exact fire count for event type E |
| `capturedEvents<E>()` | Get the `EventCapture<E>` for detailed assertions |

### Performance Metrics

| Method | Description |
|---|---|
| `collectMetrics()` | Returns `TestMetrics` with TPS, memory, duration, event count |

`TestMetrics` fields: `startTps`, `endTps`, `avgTps`, `peakMemoryMb`, `memoryDeltaMb`, `durationMs`, `eventCount`.

The runner automatically collects and displays metrics on PASS.

### Other

| Member | Description |
|---|---|
| `operator` | Player who ran the test |
| `players` | List of spawned fake players (dynamically updated in live mode) |
| `instance` | The game instance |
| `gameMode` | The installed GameMode (nullable) |
| `tracker` | PlayerTracker from gameMode |
| `phase` | Current GamePhase |
| `events` | `EventRecorder` for capturing and asserting events |
| `log(message)` | Send gray message to operator |

## GameTestRunner

Manages test lifecycle:
1. Creates a temporary flat-world instance (bedrock + stone + grass)
2. Optionally creates a GameMode via the factory
3. Installs a temporary `AsyncPlayerConfigurationEvent` interceptor that routes test bot UUIDs to the GameMode's `activeInstance` (or the flat instance if no GameMode)
4. Installs the GameMode on the global event handler via a child `EventNode` (so cleanup can remove all listeners cleanly)
5. Creates and installs `EventRecorder` on the global handler
6. Spawns N fake players using `FakePlayerConnection` — they go through `doConfiguration` and `transitionConfigToPlay`, landing in the correct instance via the interceptor, which triggers `PlayerSpawnEvent` and the GameMode's `handlePlayerJoin()`
7. Waits for all bots to come online (10s timeout)
8. If GameMode is present, waits for all bots to be tracked by `PlayerTracker` (10s timeout)
9. Runs `beforeEach` hook (if defined)
10. Runs the setup block in a virtual thread
11. Runs `afterEach` hook in finally block (even on failure)
12. Collects `TestMetrics` (TPS, memory, duration, event count) and reports PASS/FAIL/TIMEOUT/ERROR to operator
13. Uninstalls `EventRecorder`
14. Cleans up: removes config interceptor node, removes bots, evicts bot UUIDs from `PlayerCache`, shuts down GameMode (which removes its core event node), unregisters test instance

Tests are isolated per operator. Only one test runs per operator at a time.

### Live Session Management

`GameTestRunner` also manages live test sessions via `startLive()`, `stopLive()`, `getLiveSession()`. Live sessions are stored per operator UUID and coexist independently from standard tests.

### GameMode Integration

The GameMode's `install()` method wraps its core event listeners (`PlayerSpawnEvent`, `PlayerDisconnectEvent`, `PlayerUseItemEvent`) in a child `EventNode` named `gamemode-core-<name>`. On `shutdown()`, this node is removed from the global handler, ensuring no zombie listeners persist after test cleanup.

The test runner's config interceptor ensures fake players are routed to the GameMode's instance (not the live server's mode instance) during the configuration phase. This means `PlayerSpawnEvent` fires with `isFirstSpawn = true` in the correct instance, allowing the GameMode to call `handlePlayerJoin()` normally.

### PlayerCache Handling

Orbit's global `AsyncPlayerConfigurationEvent` handler calls `PlayerCache.preload()` for all new players, including test bots. Since test bots have no real Gravity data, the cache entry will contain null/default values (`player = null`, default `LevelData`, `EconomyData`, etc.). The test runner evicts bot cache entries during cleanup.

GameMode's `handlePlayerJoin()` does not directly depend on PlayerCache data (it checks `VanishManager.isVanished` which is tag-based, and uses `tracker.join`). Translation calls only happen for the host owner's force-start item, which won't match test bot UUIDs.

## LiveTestSession

A live test session attaches to the **current running GameMode** on the operator's instance. The operator is a real player in the game.

### Capabilities
- **Bot spawning**: Spawns bots into the live game using `FakePlayerConnection` + `doConfiguration` + `transitionConfigToPlay` with a temporary config interceptor routing bots to the game instance. Bots trigger `handlePlayerJoin()` naturally via `PlayerSpawnEvent`.
- **Bot removal**: Disconnects each bot via `player.playerConnection.disconnect()`, triggering `PlayerDisconnectEvent` and `handlePlayerDisconnect()` naturally. Cleans up `PlayerCache` entries.
- **Dynamic context**: `context()` returns a `GameTestContext` with `mutablePlayers = true`, so `players` always reflects the current set of live bots.
- **Script execution**: `runScript(definition)` runs a `GameTestDefinition`'s setup block against the live game in a virtual thread. Spawns additional bots if `playerCount` exceeds current count.
- **Cleanup**: `stop()` removes all spawned bots and cleans up event nodes.

### Bot UUID Scheme
Live test bots use UUID prefix `0x00FE57B0_10000000` to distinguish from standard gametest bots (`0x00FE57B0_00000000`) and FakePlayerManager bots (`0x00FA0EB0_00000000`).

## Event Recording

`EventRecorder` captures events during tests for later assertion. It registers listeners on a child `EventNode` attached to the global handler.

```kotlin
gameTest("chat-event-fires") {
    setup {
        events.record<PlayerChatEvent>()
        players[0].simulateChat("hello")
        waitTicks(2)
        assertEventFired<PlayerChatEvent>()
        assertEventCount<PlayerChatEvent>(1)
        val captured = capturedEvents<PlayerChatEvent>()
        assert(captured.first().rawMessage == "hello")
    }
}
```

### EventCapture

| Member | Description |
|---|---|
| `events` | All captured events (snapshot) |
| `count` | Number of captured events |
| `first()` | First captured event |
| `last()` | Most recent captured event |
| `any(predicate)` | True if any event matches |
| `none(predicate)` | True if no event matches |
| `filter(predicate)` | Filter captured events |
| `clear()` | Reset captured events |

The recorder is automatically installed before each test and uninstalled during cleanup.

## Test Lifecycle Hooks

```kotlin
gameTest("with-hooks") {
    beforeEach {
        log("setting up")
    }
    afterEach {
        log("cleaning up")
    }
    setup {
        assert(true)
    }
}
```

- `beforeEach` runs before the setup block, receives the `GameTestContext`
- `afterEach` runs after setup, even on failure (in finally block), receives the `GameTestContext`

## Test Tags/Groups

```kotlin
gameTest("fast-test") {
    tags = listOf("smoke", "fast")
    setup { assert(true) }
}
```

- `GameTestRegistry.byTag(tag)` returns all tests with a specific tag
- `GameTestRegistry.tags()` returns all unique tags across registered tests
- `GameTestRunner.runByTag(operator, tag)` runs all tests with a specific tag
- `/orbit test runtag <tag>` command runs tests by tag

## Test Fixtures

Fixtures provide reusable defaults for test configuration.

```kotlin
val stressFixture = fixture {
    playerCount = 20
    timeout = 60.seconds
    gameModeFactory = { SomeGameMode(settings) }
}

gameTest("stress-test") {
    useFixture(stressFixture)
    setup {
        waitForPhase(GamePhase.PLAYING)
        assertAliveCount(20)
    }
}
```

`GameTestFixture` fields: `gameModeFactory`, `playerCount`, `timeout`, `playerSetup`.

`useFixture(fixture)` copies the fixture's `gameModeFactory`, `playerCount`, and `timeout` into the builder.

## Command

The gametest functionality is available under `/orbit test` (permission: `orbit.admin`).

| Syntax | Description |
|---|---|
| `/orbit test run <testId>` | Run a specific test |
| `/orbit test runall` | Run all registered tests sequentially |
| `/orbit test runtag <tag>` | Run all tests with a specific tag |
| `/orbit test cancel` | Cancel the running test |
| `/orbit test list` | List all registered tests |
| `/orbit test live` | Start live test session (attach to current game) |
| `/orbit test live stop` | End live session, remove spawned bots |
| `/orbit test live spawn <count>` | Spawn N bots into the current game |
| `/orbit test live fill <count>` | Spawn bots to fill game to N players total |
| `/orbit test live bots` | List spawned bots with alive/eliminated state |
| `/orbit test live kill <player>` | Kill a specific player/bot |
| `/orbit test live damage <player> <amount>` | Damage a player/bot |
| `/orbit test live phase` | Show current game phase |
| `/orbit test live assert alive <count>` | Assert alive player count |
| `/orbit test live assert phase <phase>` | Assert current game phase |
| `/orbit test live run <testId>` | Run a scripted test against the live game |
| `/orbit test live disconnect <player>` | Disconnect a bot from the game |
| `/orbit test live reconnect <player>` | Force reconnect a disconnected bot (or spawn replacement) |
| `/orbit test live mutualkill <p1> <p2>` | Both players die on the same tick |
| `/orbit test live alldisconnect` | Eliminate all bots simultaneously |
| `/orbit test live forcestart` | Force start game below minimum players |
| `/orbit test live forcephase <phase>` | Force phase transition (PLAYING or ENDING) |

Tab completion for player names in `kill`, `damage`, `disconnect`, `reconnect`, and `mutualkill` includes both real players and bots in the session's instance.

The standalone `/gametest` command (permission: `nebula.gametest`) is deprecated and will be removed in a future version. Use `/orbit test` instead.

## Reporting

- `PASS` — green, with duration
- `FAIL` — red, with assertion message and duration
- `TIMEOUT` — red, when test exceeds its timeout
- `ERROR` — red, for unexpected exceptions
- `runall` shows a summary line at the end

## Fluent Assertion DSL (`GameTestAssertions.kt`)

Infix extensions that throw `GameTestFailure` on failure. Importable into any test context.

```kotlin
tracker!!.aliveCount shouldBe 3
tracker!!.killsOf(player.uuid) shouldBeGreaterThan 0
players.shouldNotBeEmpty()
player.health shouldBe 20.0f
tracker!!.teamOf(player.uuid).shouldNotBeNull()
```

| Function | Description |
|---|---|
| `Int shouldBe expected` | Int equality |
| `Double shouldBe expected` | Double equality |
| `Float shouldBe expected` | Float equality |
| `Int shouldBeGreaterThan n` | Int > n |
| `Int shouldBeLessThan n` | Int < n |
| `Int shouldBeAtLeast n` | Int >= n |
| `Int shouldBeAtMost n` | Int <= n |
| `T shouldBe expected` | Generic equality |
| `T shouldNotBe expected` | Generic inequality |
| `T?.shouldNotBeNull()` | Non-null check, returns T |
| `Boolean.shouldBeTrue(message)` | Must be true |
| `Boolean.shouldBeFalse(message)` | Must be false |
| `Collection.shouldBeEmpty()` | Must be empty |
| `Collection.shouldNotBeEmpty()` | Must not be empty |
| `Collection.shouldContain(element)` | Must contain element |
| `Collection shouldHaveSize n` | Must have size n |

## Edge Case Testing (`GameTestEdgeCases.kt`)

Top-level extension functions on `GameTestContext` for reproducing hard-to-trigger scenarios. Import from `me.nebula.orbit.utils.gametest`.

### Concurrency Testing

| Function | Description |
|---|---|
| `concurrent(vararg actions)` | Execute all actions on the same tick via scheduler, wait for completion |
| `forAllPlayers { player -> }` | Execute action on all players simultaneously on the same tick |
| `stressRepeat(times) { }` | Run action N times rapidly in a tight loop |

### Timing Edge Cases

| Function | Description |
|---|---|
| `atTickStart { }` | Schedule action at next tick start, wait for completion |
| `atTickEnd { }` | Schedule action one tick after next tick, wait for completion |
| `rapidFire(ticks) { tick -> }` | Execute action every tick for N ticks |
| `withLatency(ticks) { }` | Delay block execution by N ticks |

### Player State Edge Cases

| Function | Description |
|---|---|
| `simulateDisconnectReconnect(player, disconnectTicks)` | Simulate disconnect via handleDeath, wait N ticks (default 20) |
| `simulateJoinDuringPhaseTransition(phase)` | Wait for phase then spawn a bot (live mode only) |
| `simulateAllDisconnect()` | Eliminate all players simultaneously |
| `simulateRapidSpectatorSwitch(player, targets, intervalTicks)` | Cycle player through spectator targets rapidly |
| `forcePhase(phase)` | Force game to a specific phase (PLAYING via forceStart, ENDING via forceEnd) |
| `simulateJoinWithFullInventory(player)` | Fill every inventory slot with dirt stacks |
| `simulateMoveToWorldBorder(player)` | Teleport player to world border edge (29,999,984) |
| `simulateMoveToVoid(player)` | Teleport player to Y=-64 |

### Combat Edge Cases

| Function | Description |
|---|---|
| `simulateMutualKill(player1, player2)` | Both players die on the same tick via scheduler |
| `simulateMultiDamage(player, sources)` | Apply damage from multiple attackers on the same tick |
| `simulateEnvironmentDeath(player, type)` | Kill with no attacker (default OUT_OF_WORLD) |
| `simulateCombatLog(player)` | Record recent damage then disconnect the player |
| `simulateTeamKill(attacker, victim)` | Force damage + handleDeath with a teammate as killer |

### Game Flow Edge Cases

| Function | Description |
|---|---|
| `simulateForceStartUnderMin()` | Call forceStart regardless of player count |
| `simulateSimultaneousElimination(players)` | Eliminate all listed players on the same tick |
| `simulateTimerExpire()` | Force end with draw result |
| `simulateOvertimeTrigger()` | Force end with draw result (overtime scenario) |
| `awaitGameEnd(timeout)` | Wait for ENDING phase (default 30s timeout) |
| `resetAndVerify()` | Force end, wait for WAITING, verify clean state |

### Inventory Edge Cases

| Function | Description |
|---|---|
| `simulateFillInventory(player, item)` | Fill every slot (default: dirt x64) |
| `simulateInventoryOverflow(player, item)` | Fill inventory then attempt to add one more item |
| `simulatePickupItem(player, item)` | Add item to inventory (simulates ground pickup) |

### Test Generation Helpers

```kotlin
parameterizedTest("damage-values", listOf(
    mapOf("amount" to 1.0f),
    mapOf("amount" to 10.0f),
    mapOf("amount" to Float.MAX_VALUE),
)) { params ->
    setup {
        val amount = params["amount"] as Float
        players[0].simulateDamage(amount)
        waitTicks(5)
    }
}

playerCountTests("scaling", counts = 2..16, step = 2) { count ->
    setup {
        waitForPhase(GamePhase.PLAYING)
        assertAliveCount(count)
    }
}
```

| Function | Description |
|---|---|
| `parameterizedTest(baseId, params) { }` | Generate tests for each parameter set (IDs: `baseId-0`, `baseId-1`, ...) |
| `playerCountTests(baseId, counts, step) { }` | Generate tests for player count range (IDs: `baseId-2p`, `baseId-4p`, ...) |

## Raw Tests (no GameMode)

Omit `withGameMode { }` to test without a game mode. Players spawn in a flat world. Use `simulateAttack`, `simulateMove`, etc. for raw player interaction testing.

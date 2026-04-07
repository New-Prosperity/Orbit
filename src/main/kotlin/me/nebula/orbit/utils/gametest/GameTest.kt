package me.nebula.orbit.utils.gametest

import me.nebula.orbit.mode.game.GameMode
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object GameTestRegistry {

    private val tests = ConcurrentHashMap<String, GameTestDefinition>()

    fun register(definition: GameTestDefinition) {
        tests[definition.id] = definition
    }

    fun get(id: String): GameTestDefinition? = tests[id]

    fun all(): Map<String, GameTestDefinition> = tests.toMap()

    fun ids(): Set<String> = tests.keys.toSet()

    fun tags(): Set<String> = tests.values.flatMapTo(mutableSetOf()) { it.tags }

    fun byTag(tag: String): Map<String, GameTestDefinition> =
        tests.filterValues { tag in it.tags }

    fun clear() = tests.clear()
}

data class GameTestDefinition(
    val id: String,
    val description: String,
    val playerCount: Int,
    val timeout: Duration,
    val gameModeFactory: (() -> GameMode)?,
    val live: Boolean,
    val tags: List<String>,
    val beforeEach: (GameTestContext.() -> Unit)?,
    val afterEach: (GameTestContext.() -> Unit)?,
    val setup: GameTestContext.() -> Unit,
)

class GameTestFixture(
    val gameModeFactory: (() -> GameMode)? = null,
    val playerCount: Int = 2,
    val timeout: Duration = 30.seconds,
    val playerSetup: (GameTestContext.(net.minestom.server.entity.Player) -> Unit)? = null,
)

class GameTestFixtureBuilder @PublishedApi internal constructor() {
    var gameModeFactory: (() -> GameMode)? = null
    var playerCount: Int = 2
    var timeout: Duration = 30.seconds
    var playerSetup: (GameTestContext.(net.minestom.server.entity.Player) -> Unit)? = null

    @PublishedApi internal fun build(): GameTestFixture = GameTestFixture(
        gameModeFactory = gameModeFactory,
        playerCount = playerCount,
        timeout = timeout,
        playerSetup = playerSetup,
    )
}

inline fun fixture(block: GameTestFixtureBuilder.() -> Unit): GameTestFixture =
    GameTestFixtureBuilder().apply(block).build()

class GameTestBuilder @PublishedApi internal constructor(
    @PublishedApi internal val id: String,
) {
    var description: String = ""
    var playerCount: Int = 2
    var timeout: Duration = 30.seconds
    var live: Boolean = false
    var tags: List<String> = emptyList()

    @PublishedApi internal var gameModeFactory: (() -> GameMode)? = null
    @PublishedApi internal var setupBlock: (GameTestContext.() -> Unit)? = null
    @PublishedApi internal var beforeEachBlock: (GameTestContext.() -> Unit)? = null
    @PublishedApi internal var afterEachBlock: (GameTestContext.() -> Unit)? = null

    fun withGameMode(factory: () -> GameMode) {
        gameModeFactory = factory
    }

    fun live() { live = true }

    fun useFixture(f: GameTestFixture) {
        if (f.gameModeFactory != null) gameModeFactory = f.gameModeFactory
        playerCount = f.playerCount
        timeout = f.timeout
    }

    fun beforeEach(block: GameTestContext.() -> Unit) {
        beforeEachBlock = block
    }

    fun afterEach(block: GameTestContext.() -> Unit) {
        afterEachBlock = block
    }

    fun setup(block: GameTestContext.() -> Unit) {
        setupBlock = block
    }

    @PublishedApi internal fun build(): GameTestDefinition {
        val setup = requireNotNull(setupBlock) { "GameTest '$id' requires a setup block" }
        return GameTestDefinition(
            id = id,
            description = description,
            playerCount = playerCount,
            timeout = timeout,
            gameModeFactory = gameModeFactory,
            live = live,
            tags = tags,
            beforeEach = beforeEachBlock,
            afterEach = afterEachBlock,
            setup = setup,
        )
    }
}

inline fun gameTest(id: String, block: GameTestBuilder.() -> Unit): GameTestDefinition {
    val definition = GameTestBuilder(id).apply(block).build()
    GameTestRegistry.register(definition)
    return definition
}

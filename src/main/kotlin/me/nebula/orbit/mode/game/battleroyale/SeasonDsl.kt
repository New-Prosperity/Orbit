package me.nebula.orbit.mode.game.battleroyale

import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.HotbarItemConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.config.LobbyWorldConfig
import me.nebula.orbit.mode.config.ScoreboardConfig
import me.nebula.orbit.mode.config.SpawnConfig
import me.nebula.orbit.mode.config.TabListConfig
import me.nebula.orbit.mode.game.TimingConfig
import me.nebula.orbit.utils.chestloot.ChestLootBuilder
import me.nebula.orbit.utils.chestloot.ChestLootTable

@DslMarker
annotation class SeasonDslMarker

@SeasonDslMarker
class SeasonBuilder @PublishedApi internal constructor(private val id: Int) {

    @PublishedApi internal val kits = mutableListOf<KitDefinitionConfig>()
    @PublishedApi internal val xpRewards = mutableMapOf<String, Long>()
    @PublishedApi internal var starterKit = StarterKitConfig()
    @PublishedApi internal val voteCategories = mutableListOf<VoteCategoryDef>()
    @PublishedApi internal val lootTables = mutableListOf<ChestLootTable>()
    @PublishedApi internal var worldPath = "worlds/battleroyale"
    @PublishedApi internal var preloadRadius = 8
    @PublishedApi internal var spawn = SpawnConfig(0.5, 65.0, 0.5, 0f, 0f)
    @PublishedApi internal var scoreboard = ScoreboardConfig("orbit.game.br.scoreboard.title", 2, emptyList())
    @PublishedApi internal var tabList = TabListConfig(3, "orbit.game.br.tablist.header", "orbit.game.br.tablist.footer")
    @PublishedApi internal var lobby = LobbyConfig("ADVENTURE", protectBlocks = true, disableDamage = true, disableHunger = true, lockInventory = false, voidTeleportY = -64.0)
    @PublishedApi internal var hotbar = listOf<HotbarItemConfig>()
    @PublishedApi internal var timing = TimingConfig(countdownSeconds = 15, endingDurationSeconds = 10, minPlayers = 2, maxPlayers = 16)
    @PublishedApi internal var border = BorderConfig(500.0, 20.0, 0.0, 0.0, 120, 300)
    @PublishedApi internal val borderPhases = mutableListOf<BorderPhaseConfig>()
    @PublishedApi internal var borderDamagePerSecond = 1f
    @PublishedApi internal var spawnMode = SpawnModeConfig()
    @PublishedApi internal var goldenHead = GoldenHeadConfig()
    @PublishedApi internal var deathmatch = DeathmatchConfig()
    @PublishedApi internal var cosmetics = CosmeticConfig()
    @PublishedApi internal var mapPreset: String? = null
    @PublishedApi internal var lobbyWorld: LobbyWorldConfig? = null

    fun xp(vararg rewards: Pair<String, Long>) {
        xpRewards.putAll(rewards)
    }

    inline fun starterKit(block: EquipmentBuilder.() -> Unit) {
        starterKit = EquipmentBuilder().apply(block).buildStarterKit()
    }

    inline fun kit(id: String, block: KitDefBuilder.() -> Unit) {
        kits += KitDefBuilder(id).apply(block).build()
    }

    inline fun vote(id: String, block: VoteCategoryBuilder.() -> Unit) {
        voteCategories += VoteCategoryBuilder(id).apply(block).build()
    }

    inline fun lootTable(name: String, block: ChestLootBuilder.() -> Unit) {
        lootTables += ChestLootBuilder(name).apply(block).build()
    }

    fun world(path: String, preload: Int = 8) {
        worldPath = path
        preloadRadius = preload
    }

    fun spawn(x: Double, y: Double, z: Double, yaw: Float = 0f, pitch: Float = 0f) {
        spawn = SpawnConfig(x, y, z, yaw, pitch)
    }

    fun scoreboard(title: String, refresh: Int, lines: List<String>) {
        scoreboard = ScoreboardConfig(title, refresh, lines)
    }

    fun tabList(refresh: Int, header: String, footer: String) {
        tabList = TabListConfig(refresh, header, footer)
    }

    fun lobby(block: LobbyConfig) { lobby = block }

    fun timing(block: TimingConfig) { timing = block }

    fun border(initialDiameter: Double, finalDiameter: Double, centerX: Double = 0.0, centerZ: Double = 0.0, shrinkStart: Int = 120, shrinkDuration: Int = 300) {
        border = BorderConfig(initialDiameter, finalDiameter, centerX, centerZ, shrinkStart, shrinkDuration)
    }

    fun borderPhase(startAfter: Int, targetDiameter: Double, shrinkDuration: Int, damage: Float = 1f) {
        borderPhases += BorderPhaseConfig(startAfter, targetDiameter, shrinkDuration, damage)
    }

    fun borderDamage(damage: Float) { borderDamagePerSecond = damage }

    fun spawnMode(config: SpawnModeConfig) { spawnMode = config }

    fun goldenHead(config: GoldenHeadConfig) { goldenHead = config }

    fun deathmatch(config: DeathmatchConfig) { deathmatch = config }

    fun cosmetics(config: CosmeticConfig) { cosmetics = config }

    fun mapPreset(preset: String) { mapPreset = preset }

    fun lobbyWorld(path: String, preload: Int = 4, spawnConfig: SpawnConfig = SpawnConfig(0.5, 65.0, 0.5, 0f, 0f)) {
        lobbyWorld = LobbyWorldConfig(path, preload, spawnConfig)
    }

    @PublishedApi internal fun build() = Season(
        id = id,
        kits = kits.toList(),
        xpRewards = xpRewards.toMap(),
        starterKit = starterKit,
        voteCategories = voteCategories.toList(),
        lootTables = lootTables.toList(),
        worldPath = worldPath,
        preloadRadius = preloadRadius,
        spawn = spawn,
        scoreboard = scoreboard,
        tabList = tabList,
        lobby = lobby,
        hotbar = hotbar,
        timing = timing,
        border = border,
        borderPhases = borderPhases.toList(),
        borderDamagePerSecond = borderDamagePerSecond,
        spawnMode = spawnMode,
        goldenHead = goldenHead,
        deathmatch = deathmatch,
        cosmetics = cosmetics,
        mapPreset = mapPreset,
        lobbyWorld = lobbyWorld,
    )
}

@SeasonDslMarker
class KitDefBuilder @PublishedApi internal constructor(private val id: String) {

    @PublishedApi internal var nameKey = ""
    @PublishedApi internal var descriptionKey = ""
    @PublishedApi internal var material = "minecraft:barrier"
    @PublishedApi internal var locked = true
    @PublishedApi internal var maxLevel = 3
    @PublishedApi internal var xpPerLevel = listOf<Long>()
    @PublishedApi internal val tiers = mutableMapOf<Int, KitTierConfig>()

    fun name(baseKey: String) {
        nameKey = "$baseKey.name"
        descriptionKey = "$baseKey.desc"
    }

    fun icon(key: String) { material = key }
    fun unlocked() { locked = false }
    fun locked() { locked = true }

    fun levels(max: Int, xp: List<Long>) {
        maxLevel = max
        xpPerLevel = xp
    }

    inline fun tier(level: Int, block: EquipmentBuilder.() -> Unit) {
        tiers[level] = EquipmentBuilder().apply(block).buildTier()
    }

    @PublishedApi internal fun build() = KitDefinitionConfig(
        id = id,
        nameKey = nameKey,
        descriptionKey = descriptionKey,
        material = material,
        locked = locked,
        maxLevel = maxLevel,
        xpPerLevel = xpPerLevel,
        tiers = tiers.toMap(),
    )
}

@SeasonDslMarker
class EquipmentBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var helmet: String? = null
    @PublishedApi internal var chestplate: String? = null
    @PublishedApi internal var leggings: String? = null
    @PublishedApi internal var boots: String? = null
    @PublishedApi internal val items = mutableListOf<StarterKitItemConfig>()

    fun armor(prefix: String) {
        val name = if (prefix.contains(':')) prefix.substringAfter(':') else prefix
        helmet = "minecraft:${name}_helmet"
        chestplate = "minecraft:${name}_chestplate"
        leggings = "minecraft:${name}_leggings"
        boots = "minecraft:${name}_boots"
    }

    fun helmet(key: String) { helmet = key }
    fun chestplate(key: String) { chestplate = key }
    fun leggings(key: String) { leggings = key }
    fun boots(key: String) { boots = key }
    fun item(slot: Int, material: String, amount: Int = 1) { items += StarterKitItemConfig(slot, material, amount) }

    @PublishedApi internal fun buildTier() = KitTierConfig(helmet, chestplate, leggings, boots, items.toList())
    @PublishedApi internal fun buildStarterKit() = StarterKitConfig(helmet, chestplate, leggings, boots, items.toList())
}

@SeasonDslMarker
class VoteCategoryBuilder @PublishedApi internal constructor(private val id: String) {

    @PublishedApi internal var nameKey = ""
    @PublishedApi internal var material = "minecraft:paper"
    @PublishedApi internal var defaultIndex = 1
    @PublishedApi internal val options = mutableListOf<VoteOptionDef>()

    fun name(key: String) { nameKey = key }
    fun icon(key: String) { material = key }
    fun default(index: Int) { defaultIndex = index }

    fun option(
        nameKey: String,
        material: String,
        value: Int,
        mapIcon: String? = null,
        descriptionKey: String? = null,
    ) {
        options += VoteOptionDef(nameKey, material, value, mapIcon = mapIcon, descriptionKey = descriptionKey)
    }

    @PublishedApi internal fun build() = VoteCategoryDef(
        id = id,
        nameKey = nameKey,
        material = material,
        defaultIndex = defaultIndex,
        options = options.toList(),
    )
}

inline fun season(id: Int, block: SeasonBuilder.() -> Unit): Season =
    SeasonBuilder(id).apply(block).build()

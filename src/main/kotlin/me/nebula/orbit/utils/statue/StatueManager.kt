package me.nebula.orbit.utils.statue

import com.google.gson.reflect.TypeToken
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.leveling.LevelStore
import me.nebula.gravity.player.PlayerStore
import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.ranking.Periodicity
import me.nebula.gravity.ranking.RankingStore
import me.nebula.gravity.ranking.rankingKey
import me.nebula.gravity.rating.EloCalculator
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.sanction.SanctionStore
import me.nebula.gravity.sanction.isBanned
import me.nebula.orbit.Orbit
import me.nebula.orbit.cosmetic.CosmeticApplier
import me.nebula.orbit.cosmetic.CosmeticListener
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.utils.customcontent.armor.ArmorPart
import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.armor.createItem
import me.nebula.orbit.utils.customcontent.armor.hasSlot
import me.nebula.orbit.utils.hologram.Hologram
import me.nebula.orbit.utils.hologram.hologram
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import me.nebula.orbit.utils.modelengine.modeledEntity
import me.nebula.orbit.utils.npc.Npc
import me.nebula.orbit.utils.npc.npc
import me.nebula.orbit.utils.particle.spawnParticleCircle
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

private val logger = logger("StatueManager")

data class StatueConfig(
    val playerUuid: UUID,
    val position: Pos,
    val instance: Instance,
    val rotationSpeed: Float = 0f,
    val showCosmetics: Boolean = true,
    val showHologram: Boolean = true,
    val label: String? = null,
    val leaderboardSource: String = "rating:battleroyale",
    val leaderboardPeriod: String = "ALL_TIME",
    val autoManaged: Boolean = false,
)

data class ActiveStatue(
    val id: String,
    val config: StatueConfig,
    val npc: Npc?,
    val modelOwner: StandaloneModelOwner?,
    val hologram: Hologram?,
    val playerName: String,
    var petEntity: EntityCreature? = null,
    var petModeled: ModeledEntity? = null,
    var companionOwner: StandaloneModelOwner? = null,
    var cachedCosmetics: CosmeticPlayerData? = null,
    var cosmeticCacheTime: Long = 0,
    var skinFetchTime: Long = System.currentTimeMillis(),
)

data class StatuePodium(
    val position: Pos,
    val rank: Int,
)

data class SavedStatueConfig(
    val id: String,
    val playerUuid: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val rotationSpeed: Float,
    val showCosmetics: Boolean,
    val showHologram: Boolean,
    val label: String?,
    val leaderboardSource: String,
    val leaderboardPeriod: String,
    val autoManaged: Boolean,
)

object StatueManager {

    private val statues = ConcurrentHashMap<String, ActiveStatue>()
    private val autoStatueIds = ConcurrentHashMap.newKeySet<String>()
    private var tickTask: Task? = null
    private var refreshTask: Task? = null
    private var tickCounter = 0L
    private var currentLeaderboardSource = "rating:battleroyale"
    private var currentLeaderboardPeriod = "ALL_TIME"

    fun install() {
        PlayerModelGenerator.registerBlueprint()
        tickTask = repeat(1) { tick() }
        loadConfigs()
        logger.info { "StatueManager installed" }
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        refreshTask?.cancel()
        refreshTask = null
        removeAll()
        logger.info { "StatueManager uninstalled" }
    }

    fun spawn(id: String, config: StatueConfig): ActiveStatue? {
        if (statues.containsKey(id)) {
            logger.warn { "Statue '$id' already exists" }
            return null
        }

        val sanctionData = SanctionStore.load(config.playerUuid)
        if (sanctionData != null && sanctionData.isBanned()) {
            logger.info { "Skipping statue '$id' — player ${config.playerUuid} is banned" }
            return null
        }

        val playerData = PlayerStore.load(config.playerUuid)
        val rankData = RankManager.rankOf(config.playerUuid)
        val cosmeticData = if (config.showCosmetics) CosmeticStore.load(config.playerUuid) else null
        val levelData = LevelStore.load(config.playerUuid)
        val ratingData = RatingStore.load(config.playerUuid)

        val playerName = playerData?.name ?: "Unknown"
        val skin = fetchSkin(config.playerUuid)

        val rankColor = rankData?.color ?: "white"
        val rankName = rankData?.name ?: "Member"

        val modelResult = trySpawnModel(config, skin)
        val npcEntity = if (modelResult != null) {
            modelResult.skinNpc
        } else {
            spawnNpc(playerName, skin, config, cosmeticData)
        }
        val modelOwner = modelResult?.modelOwner

        val hologram = if (config.showHologram) {
            val hologramPos = config.position.add(0.0, 2.6, 0.0)
            config.instance.hologram(hologramPos) {
                lineSpacing = 0.28

                if (config.label != null) {
                    line("<yellow><bold>${config.label}")
                }

                line("<$rankColor><bold>$playerName")
                line("<gray>$rankName")

                val level = levelData?.level ?: 1
                line("<aqua>Level $level")

                val bestRating = ratingData?.ratings?.values?.maxByOrNull { it.rating }
                if (bestRating != null) {
                    val tier = EloCalculator.tierOf(bestRating.rating)
                    line("${tier.color}${tier.displayName} <gray>(${bestRating.rating})")
                }
            }
        } else null

        for (player in config.instance.players) {
            npcEntity.show(player)
            modelOwner?.show(player)
        }

        val statue = ActiveStatue(
            id = id,
            config = config,
            npc = npcEntity,
            modelOwner = modelOwner,
            hologram = hologram,
            playerName = playerName,
            cachedCosmetics = cosmeticData,
            cosmeticCacheTime = System.currentTimeMillis(),
            skinFetchTime = System.currentTimeMillis(),
        )

        spawnPetCompanion(statue, cosmeticData)

        statues[id] = statue
        logger.info { "Spawned statue '$id' for player $playerName (model=${modelOwner != null})" }

        if (!config.autoManaged) saveConfigs()

        return statue
    }

    private fun spawnPetCompanion(statue: ActiveStatue, cosmeticData: CosmeticPlayerData?) {
        if (cosmeticData == null) return
        val config = statue.config

        val petCosmeticId = cosmeticData.equipped[CosmeticCategory.PET.name]
        if (petCosmeticId != null) {
            val definition = CosmeticRegistry[petCosmeticId]
            if (definition != null) {
                val level = cosmeticData.owned[petCosmeticId] ?: 1
                val resolved = definition.resolveData(level)
                val modelId = resolved["modelId"]
                if (modelId != null && ModelEngine.blueprintOrNull(modelId) != null) {
                    val scale = resolved["scale"]?.toFloatOrNull() ?: 1.0f
                    val petPos = config.position.add(1.5, 0.0, 1.5)
                    val creature = EntityCreature(EntityType.ZOMBIE)
                    creature.isInvisible = true
                    creature.isSilent = true
                    creature.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.08
                    creature.setInstance(config.instance, petPos).thenRun {
                        if (creature.isRemoved) return@thenRun
                        val modeled = modeledEntity(creature) {
                            model(modelId, autoPlayIdle = true) { scale(scale) }
                        }
                        for (player in config.instance.players) {
                            modeled.show(player)
                        }
                        statue.petEntity = creature
                        statue.petModeled = modeled
                    }
                }
            }
        }

        val companionId = cosmeticData.equipped[CosmeticCategory.COMPANION.name]
        if (companionId != null) {
            val definition = CosmeticRegistry[companionId]
            if (definition != null) {
                val level = cosmeticData.owned[companionId] ?: 1
                val resolved = definition.resolveData(level)
                val modelId = resolved["modelId"]
                if (modelId != null && ModelEngine.blueprintOrNull(modelId) != null) {
                    val scale = resolved["scale"]?.toFloatOrNull() ?: 0.8f
                    val companionPos = config.position.add(-1.5, 0.5, -1.5)
                    val owner = standAloneModel(companionPos) {
                        model(modelId, autoPlayIdle = true) { scale(scale) }
                    }
                    for (player in config.instance.players) {
                        owner.show(player)
                    }
                    statue.companionOwner = owner
                }
            }
        }
    }

    private fun trySpawnModel(config: StatueConfig, skin: PlayerSkin?): ModelStatueResult? {
        if (!PlayerModelGenerator.isRegistered()) {
            logger.warn { "Blueprint '${PlayerModelGenerator.BLUEPRINT_NAME}' not registered, falling back to NPC" }
            return null
        }
        return runCatching {
            val skinNpc = spawnSkinNpc(config, skin)
            val owner = standAloneModel(config.position) {
                model(PlayerModelGenerator.BLUEPRINT_NAME, autoPlayIdle = true)
            }
            ModelStatueResult(skinNpc, owner)
        }.onFailure {
            logger.warn { "ModelEngine statue failed for ${config.playerUuid}, falling back to NPC: ${it.message}" }
        }.getOrNull()
    }

    private fun spawnSkinNpc(config: StatueConfig, skin: PlayerSkin?): Npc = npc("") {
        skin?.let { skin(it) }
        position(config.position)
        lookAtPlayer(false)
        nameOffset(-100.0)
        onClick { viewer ->
            onStatueClicked(viewer, config)
        }
    }

    private fun spawnNpc(
        playerName: String,
        skin: PlayerSkin?,
        config: StatueConfig,
        cosmeticData: CosmeticPlayerData?,
    ): Npc = npc(playerName) {
        skin?.let { skin(it) }
        position(config.position)
        lookAtPlayer(true)
        nameOffset(if (config.showHologram) -100.0 else 2.05)

        if (config.showCosmetics && cosmeticData != null) {
            val armorCosmeticId = cosmeticData.equipped[CosmeticCategory.ARMOR_SKIN.name]
            if (armorCosmeticId != null) {
                val definition = CosmeticRegistry[armorCosmeticId]
                if (definition != null) {
                    val level = cosmeticData.owned[armorCosmeticId] ?: 1
                    val resolved = definition.resolveData(level)
                    val armorSetId = resolved["armorId"]
                    if (armorSetId != null) {
                        val armorDef = CustomArmorRegistry[armorSetId]
                        if (armorDef != null) {
                            if (armorDef.hasSlot(EquipmentSlot.HELMET)) helmet(armorDef.createItem(ArmorPart.Helmet))
                            if (armorDef.hasSlot(EquipmentSlot.CHESTPLATE)) chestplate(armorDef.createItem(ArmorPart.Chestplate))
                            if (armorDef.hasSlot(EquipmentSlot.LEGGINGS)) leggings(armorDef.createItem(ArmorPart.InnerArmor))
                            if (armorDef.hasSlot(EquipmentSlot.BOOTS)) boots(armorDef.createItem(ArmorPart.RightBoot))
                        }
                    }
                }
            }
        }

        onClick { viewer ->
            onStatueClicked(viewer, config)
        }
    }

    private fun onStatueClicked(viewer: Player, config: StatueConfig) {
        viewer.playSound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)

        val statue = statues.values.firstOrNull { it.config === config }
        if (statue?.modelOwner != null) {
            val model = statue.modelOwner.modeledEntity?.models?.values?.firstOrNull()
            if (model != null) {
                model.playAnimation("wave", lerpIn = 0.1f, lerpOut = 0.1f, speed = 1f)
                delay(20) {
                    if (!statue.modelOwner.isRemoved) {
                        model.stopAnimation("wave")
                    }
                }
            }
        }

        val playerName = statue?.playerName ?: "Unknown"
        StatueProfileMenu.open(viewer, config.playerUuid, playerName)
    }

    fun remove(id: String): Boolean {
        val statue = statues.remove(id) ?: return false
        autoStatueIds.remove(id)
        statue.npc?.remove()
        statue.modelOwner?.remove()
        statue.hologram?.remove()
        statue.petModeled?.destroy()
        statue.petEntity?.remove()
        statue.companionOwner?.remove()
        logger.info { "Removed statue '$id'" }
        if (!statue.config.autoManaged) saveConfigs()
        return true
    }

    fun removeAll() {
        statues.keys.toList().forEach { remove(it) }
    }

    operator fun get(id: String): ActiveStatue? = statues[id]

    fun all(): Collection<ActiveStatue> = statues.values

    fun moveStatue(id: String, newPos: Pos): Boolean {
        val existing = statues[id] ?: return false
        remove(id)
        val newConfig = existing.config.copy(position = newPos)
        spawn(id, newConfig)
        return true
    }

    fun setAnimation(id: String, animationName: String): Boolean {
        val statue = statues[id] ?: return false
        val model = statue.modelOwner?.modeledEntity?.models?.values?.firstOrNull() ?: return false
        model.stopAllAnimations()
        model.playAnimation(animationName, lerpIn = 0.2f, speed = 1f)
        return true
    }

    fun setLeaderboardConfig(source: String, period: String) {
        currentLeaderboardSource = source
        currentLeaderboardPeriod = period
    }

    fun refreshTopPlayerStatues(instance: Instance, podiums: List<StatuePodium>) {
        val topPlayers = resolveTopPlayers(podiums.size)

        podiums.forEachIndexed { index, podium ->
            if (index >= topPlayers.size) return@forEachIndexed
            val ranked = topPlayers[index]
            val id = "auto_top_${podium.rank}"

            val existing = statues[id]
            if (existing != null && existing.config.playerUuid == ranked.uuid) {
                updateHologram(existing, ranked)
                return@forEachIndexed
            }

            if (existing != null) {
                remove(id)
            }

            autoStatueIds.add(id)
            spawn(id, StatueConfig(
                playerUuid = ranked.uuid,
                position = podium.position,
                instance = instance,
                rotationSpeed = 1f,
                showCosmetics = true,
                showHologram = true,
                label = "#${podium.rank} Player",
                leaderboardSource = currentLeaderboardSource,
                leaderboardPeriod = currentLeaderboardPeriod,
                autoManaged = true,
            ))
        }

        autoStatueIds.toList()
            .filter { id -> podiums.none { "auto_top_${it.rank}" == id } || statues[id] == null }
            .forEach { remove(it) }
    }

    private fun updateHologram(statue: ActiveStatue, newData: TopPlayerEntry) {
        val config = statue.config
        val rankData = RankManager.rankOf(config.playerUuid)
        val levelData = LevelStore.load(config.playerUuid)
        val ratingData = RatingStore.load(config.playerUuid)

        statue.hologram?.remove()

        val hologramPos = config.position.add(0.0, 2.6, 0.0)
        val newHologram = config.instance.hologram(hologramPos) {
            lineSpacing = 0.28

            if (config.label != null) {
                line("<yellow><bold>${config.label}")
            }

            val rankColor = rankData?.color ?: "white"
            val rankName = rankData?.name ?: "Member"
            line("<$rankColor><bold>${newData.name}")
            line("<gray>$rankName")

            val level = levelData?.level ?: 1
            line("<aqua>Level $level")

            val bestRating = ratingData?.ratings?.values?.maxByOrNull { it.rating }
            if (bestRating != null) {
                val tier = EloCalculator.tierOf(bestRating.rating)
                line("${tier.color}${tier.displayName} <gray>(${bestRating.rating})")
            }
        }

        val updated = statue.copy(hologram = newHologram)
        statues[statue.id] = updated
    }

    fun installAutoRefresh(instance: Instance, podiums: List<StatuePodium>) {
        if (podiums.isEmpty()) return
        Thread.startVirtualThread {
            refreshTopPlayerStatues(instance, podiums)
        }
        refreshTask = repeat(Duration.ofMinutes(5)) {
            Thread.startVirtualThread {
                refreshTopPlayerStatues(instance, podiums)
            }
        }
    }

    private fun tick() {
        tickCounter++
        for ((_, statue) in statues) {
            val config = statue.config

            val hasNearbyPlayer = config.instance.players.any {
                it.position.distanceSquared(config.position) < 4096.0
            }
            if (!hasNearbyPlayer) continue

            if (config.rotationSpeed > 0f && statue.npc != null) {
                val yaw = (tickCounter * config.rotationSpeed) % 360f
                for (player in config.instance.players) {
                    statue.npc.lookAt(player, config.position.withYaw(yaw).add(
                        -sin(Math.toRadians(yaw.toDouble())) * 3.0,
                        0.0,
                        cos(Math.toRadians(yaw.toDouble())) * 3.0,
                    ))
                }
            }

            if (config.showCosmetics && tickCounter % 5 == 0L) {
                val now = System.currentTimeMillis()
                val cosmeticData = if (now - statue.cosmeticCacheTime > 30_000L) {
                    val fresh = CosmeticStore.load(config.playerUuid)
                    statue.cachedCosmetics = fresh
                    statue.cosmeticCacheTime = now
                    fresh
                } else {
                    statue.cachedCosmetics
                }
                if (cosmeticData == null) continue

                val instance = config.instance

                val auraId = cosmeticData.equipped[CosmeticCategory.AURA.name]
                if (auraId != null && CosmeticListener.isAllowed(CosmeticCategory.AURA, auraId)) {
                    val level = cosmeticData.owned[auraId] ?: 1
                    CosmeticApplier.spawnAuraParticles(
                        instance, config.position, auraId, level,
                        ownerUuid = config.playerUuid
                    )
                }

                if (tickCounter % 20 == 0L) {
                    val trailId = cosmeticData.equipped[CosmeticCategory.TRAIL.name]
                    if (trailId != null && CosmeticListener.isAllowed(CosmeticCategory.TRAIL, trailId)) {
                        val level = cosmeticData.owned[trailId] ?: 1
                        CosmeticApplier.spawnTrailParticle(
                            instance, config.position, trailId, level,
                            ownerUuid = config.playerUuid
                        )
                    }
                }

                if (tickCounter % 100 == 0L) {
                    val killEffectId = cosmeticData.equipped[CosmeticCategory.KILL_EFFECT.name]
                    if (killEffectId != null && CosmeticListener.isAllowed(CosmeticCategory.KILL_EFFECT, killEffectId)) {
                        val level = cosmeticData.owned[killEffectId] ?: 1
                        CosmeticApplier.playKillEffect(
                            instance, config.position, killEffectId, level,
                            ownerUuid = config.playerUuid
                        )
                    }

                    val winEffectId = cosmeticData.equipped[CosmeticCategory.WIN_EFFECT.name]
                    if (winEffectId != null && CosmeticListener.isAllowed(CosmeticCategory.WIN_EFFECT, winEffectId)) {
                        val level = cosmeticData.owned[winEffectId] ?: 1
                        CosmeticApplier.playKillEffect(
                            instance, config.position, winEffectId, level,
                            ownerUuid = config.playerUuid
                        )
                    }

                    val spawnEffectId = cosmeticData.equipped[CosmeticCategory.SPAWN_EFFECT.name]
                    if (spawnEffectId != null && CosmeticListener.isAllowed(CosmeticCategory.SPAWN_EFFECT, spawnEffectId)) {
                        val level = cosmeticData.owned[spawnEffectId] ?: 1
                        CosmeticApplier.playSpawnEffect(
                            instance, config.position, spawnEffectId, level,
                            ownerUuid = config.playerUuid
                        )
                    }
                }
            }

            if (tickCounter % 10 == 0L) {
                val label = config.label ?: ""
                val particle = when {
                    "#1" in label -> Particle.FLAME
                    "#2" in label -> Particle.END_ROD
                    "#3" in label -> Particle.COMPOSTER
                    else -> null
                }
                if (particle != null) {
                    config.instance.spawnParticleCircle(
                        particle,
                        config.position,
                        radius = 1.5,
                        points = 10,
                        count = 1,
                    )
                }
            }
        }
    }

    private fun resolveTopPlayers(count: Int): List<TopPlayerEntry> {
        val periodicity = runCatching { Periodicity.valueOf(currentLeaderboardPeriod) }.getOrElse { Periodicity.ALL_TIME }
        val ranking = RankingStore.load(rankingKey(currentLeaderboardSource, periodicity))
        if (ranking != null && ranking.isNotEmpty()) {
            return ranking.take(count).map { TopPlayerEntry(it.uuid, it.name, it.score) }
        }

        val allRatings = RatingStore.entries()
        return allRatings
            .flatMap { (uuid, data) ->
                data.ratings.values.map { uuid to it.rating }
            }
            .sortedByDescending { it.second }
            .distinctBy { it.first }
            .take(count)
            .map { (uuid, rating) ->
                val name = PlayerStore.load(uuid)?.name ?: "Unknown"
                TopPlayerEntry(uuid, name, rating.toDouble())
            }
    }

    private fun fetchSkin(uuid: UUID): PlayerSkin? =
        runCatching { PlayerSkin.fromUuid(uuid.toString().replace("-", "")) }.getOrNull() // noqa: runCatching{}.getOrNull() as null check

    private fun saveConfigs() {
        runCatching {
            val saved = statues.values
                .filter { !it.config.autoManaged }
                .map { statue ->
                    val pos = statue.config.position
                    SavedStatueConfig(
                        id = statue.id,
                        playerUuid = statue.config.playerUuid.toString(),
                        x = pos.x(),
                        y = pos.y(),
                        z = pos.z(),
                        yaw = pos.yaw(),
                        pitch = pos.pitch(),
                        rotationSpeed = statue.config.rotationSpeed,
                        showCosmetics = statue.config.showCosmetics,
                        showHologram = statue.config.showHologram,
                        label = statue.config.label,
                        leaderboardSource = statue.config.leaderboardSource,
                        leaderboardPeriod = statue.config.leaderboardPeriod,
                        autoManaged = false,
                    )
                }
            val json = GsonProvider.pretty.toJson(saved)
            Orbit.app.resources.writeText("statues.json", json)
        }.onFailure {
            logger.warn { "Failed to save statue configs: ${it.message}" }
        }
    }

    private fun loadConfigs() {
        runCatching {
            if (!Orbit.app.resources.exists("statues.json")) return
            val json = Orbit.app.resources.readText("statues.json")
            val type = object : TypeToken<List<SavedStatueConfig>>() {}.type
            val saved: List<SavedStatueConfig> = GsonProvider.default.fromJson(json, type)
            var loadedCount = 0
            for (cfg in saved) {
                val uuid = runCatching { UUID.fromString(cfg.playerUuid) }.getOrNull() ?: continue
                val pos = Pos(cfg.x, cfg.y, cfg.z, cfg.yaw, cfg.pitch)
                val instance = Orbit.mode.defaultInstance
                spawn(cfg.id, StatueConfig(
                    playerUuid = uuid,
                    position = pos,
                    instance = instance,
                    rotationSpeed = cfg.rotationSpeed,
                    showCosmetics = cfg.showCosmetics,
                    showHologram = cfg.showHologram,
                    label = cfg.label,
                    leaderboardSource = cfg.leaderboardSource,
                    leaderboardPeriod = cfg.leaderboardPeriod,
                    autoManaged = false,
                ))
                loadedCount++
            }
            if (loadedCount > 0) {
                logger.info { "Loaded $loadedCount saved statues" }
            }
        }.onFailure {
            logger.warn { "Failed to load statue configs: ${it.message}" }
        }
    }
}

private data class ModelStatueResult(
    val skinNpc: Npc,
    val modelOwner: StandaloneModelOwner,
)

internal data class TopPlayerEntry(
    val uuid: UUID,
    val name: String,
    val score: Double,
)

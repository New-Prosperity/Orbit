package me.nebula.orbit.utils.supplydrop

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.event.GameEventBus
import me.nebula.orbit.mode.game.battleroyale.SpawnModeExecutor
import me.nebula.orbit.mode.game.battleroyale.zone.ZoneState
import me.nebula.orbit.utils.chestloot.ChestLootTable
import me.nebula.orbit.utils.chestloot.LootRarity
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent

data class SupplyDropScheduleConfig(
    val enabled: Boolean = true,
    val firstPhase: Int = 1,
    val dropAltitudeOffset: Double = 80.0,
    val fallSpeed: Double = 0.6,
    val announceRadius: Double = 250.0,
    val chestDurationTicks: Int = 600,
)

object PhaseRarityCurve {

    private val curves: Map<Int, Map<LootRarity, Int>> = mapOf(
        1 to mapOf(LootRarity.COMMON to 70, LootRarity.UNCOMMON to 25, LootRarity.RARE to 5),
        2 to mapOf(LootRarity.COMMON to 45, LootRarity.UNCOMMON to 35, LootRarity.RARE to 18, LootRarity.EPIC to 2),
        3 to mapOf(LootRarity.COMMON to 25, LootRarity.UNCOMMON to 35, LootRarity.RARE to 30, LootRarity.EPIC to 9, LootRarity.LEGENDARY to 1),
        4 to mapOf(LootRarity.COMMON to 10, LootRarity.UNCOMMON to 25, LootRarity.RARE to 35, LootRarity.EPIC to 22, LootRarity.LEGENDARY to 8),
        5 to mapOf(LootRarity.COMMON to 5, LootRarity.UNCOMMON to 15, LootRarity.RARE to 30, LootRarity.EPIC to 30, LootRarity.LEGENDARY to 20),
        6 to mapOf(LootRarity.UNCOMMON to 10, LootRarity.RARE to 20, LootRarity.EPIC to 30, LootRarity.LEGENDARY to 40),
    )

    private val lateGame = mapOf(
        LootRarity.RARE to 15,
        LootRarity.EPIC to 35,
        LootRarity.LEGENDARY to 50,
    )

    fun rarityWeights(phase: Int): Map<LootRarity, Int> =
        curves[phase] ?: if (phase > 6) lateGame else curves.getValue(1)

    fun headlineRarity(phase: Int): LootRarity =
        rarityWeights(phase).maxByOrNull { it.value }?.key ?: LootRarity.COMMON
}

class SupplyDropScheduler(
    private val instance: Instance,
    private val events: GameEventBus,
    private val defaultTable: ChestLootTable,
    private val config: SupplyDropScheduleConfig = SupplyDropScheduleConfig(),
) {

    private val logger = logger("SupplyDropScheduler")
    private var subscription: GameEventBus.Subscription? = null

    fun install() {
        if (subscription != null) return
        if (!config.enabled) return
        subscription = events.subscribe<GameEvent.ZoneTransition> { event ->
            val next = event.to
            if (next !is ZoneState.Shrinking) return@subscribe
            if (next.phaseIndex < config.firstPhase) return@subscribe
            scheduleFromZone(next)
        }
    }

    fun uninstall() {
        subscription?.cancel()
        subscription = null
    }

    fun scheduleFromZone(phase: ZoneState.Shrinking) {
        val centerX = phase.toCenterX
        val centerZ = phase.toCenterZ
        val groundY = SpawnModeExecutor.findSurfaceHeight(instance, centerX.toInt(), centerZ.toInt()).toDouble() + 1.0
        val target = Pos(centerX, groundY, centerZ)
        val origin = target.withY(groundY + config.dropAltitudeOffset)
        val weights = defaultTable.weightsFromRarity(PhaseRarityCurve.rarityWeights(phase.phaseIndex))
        val headline = PhaseRarityCurve.headlineRarity(phase.phaseIndex)
        launch(origin, target, weights, headline)
    }

    fun launchPersonalDrop(nearPos: Pos, rarity: LootRarity, table: ChestLootTable = defaultTable): ActiveSupplyDrop {
        val origin = nearPos.add(0.0, config.dropAltitudeOffset, 0.0)
        val weights = table.weightsFromRarity(mapOf(rarity to 100))
        return launch(origin, nearPos, weights, rarity)
    }

    fun launch(
        origin: Pos,
        target: Pos,
        tierWeightOverrides: Map<String, Int>,
        rarity: LootRarity,
        table: ChestLootTable = defaultTable,
    ): ActiveSupplyDrop {
        val drop = SupplyDrop(
            origin = origin,
            target = target,
            fallSpeed = config.fallSpeed,
            chestTable = table,
            tierWeightOverrides = tierWeightOverrides.ifEmpty { null },
            rarity = rarity,
            trailParticle = trailFor(rarity),
            landingParticle = Particle.EXPLOSION,
            landingSound = SoundEvent.ENTITY_GENERIC_EXPLODE,
            announceRadius = config.announceRadius,
            chestDurationTicks = config.chestDurationTicks,
        )
        return drop.launch(instance)
    }

    private fun trailFor(rarity: LootRarity): Particle = when (rarity) {
        LootRarity.COMMON -> Particle.SMOKE
        LootRarity.UNCOMMON -> Particle.CLOUD
        LootRarity.RARE -> Particle.FLAME
        LootRarity.EPIC -> Particle.SOUL_FIRE_FLAME
        LootRarity.LEGENDARY -> Particle.END_ROD
    }
}

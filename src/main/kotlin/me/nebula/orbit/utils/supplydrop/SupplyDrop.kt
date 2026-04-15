package me.nebula.orbit.utils.supplydrop

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.orbit.utils.chat.mm
import me.nebula.orbit.utils.loot.LootTable
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task

data class SupplyDrop(
    val origin: Pos,
    val target: Pos,
    val fallSpeed: Double = 0.5,
    val lootTable: LootTable,
    val trailParticle: Particle = Particle.FLAME,
    val landingParticle: Particle = Particle.EXPLOSION,
    val landingSound: SoundEvent = SoundEvent.ENTITY_GENERIC_EXPLODE,
    val announceRadius: Double = 100.0,
    val announceKey: TranslationKey = "orbit.supplydrop.incoming".asTranslationKey(),
    val chestDurationTicks: Int = 600,
    val onLand: ((Pos) -> Unit)? = null,
) {

    fun launch(instance: Instance): ActiveSupplyDrop = ActiveSupplyDrop(this, instance)
}

class ActiveSupplyDrop(
    private val config: SupplyDrop,
    private val instance: Instance,
) {

    private val entity: LivingEntity = LivingEntity(EntityType.ARMOR_STAND)
    @Volatile private var task: Task? = null
    @Volatile private var landed = false

    init {
        val meta = entity.entityMeta as ArmorStandMeta
        meta.setNotifyAboutChanges(false)
        meta.isInvisible = false
        meta.isHasNoGravity = true
        meta.isSmall = false
        meta.setNotifyAboutChanges(true)
        entity.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.CHEST))
        entity.setInstance(instance, config.origin).join()
        announce()
        startFall()
    }

    val isLanded: Boolean get() = landed

    fun cancel() {
        task?.cancel()
        task = null
        if (!landed) entity.remove()
    }

    private fun announce() {
        val message = mm("<gold><bold>${config.announceKey.value}")
        val radiusSq = config.announceRadius * config.announceRadius
        instance.players
            .filter { it.position.distanceSquared(config.origin) <= radiusSq }
            .forEach { it.sendMessage(message) }
    }

    private fun startFall() {
        task = repeat(1) {
            if (landed) {
                task?.cancel()
                return@repeat
            }
            val current = entity.position
            val nextY = current.y() - config.fallSpeed
            if (nextY <= config.target.y()) {
                land()
                return@repeat
            }
            entity.teleport(current.withY(nextY))
            spawnTrailParticle(current)
        }
    }

    private fun spawnTrailParticle(pos: Pos) {
        instance.sendGroupedPacket(
            ParticlePacket(
                config.trailParticle, pos.x(), pos.y(), pos.z(),
                0.1f, 0.1f, 0.1f, 0.01f, 3
            )
        )
    }

    private fun land() {
        landed = true
        task?.cancel()
        task = null
        entity.remove()

        val landPos = config.target

        instance.sendGroupedPacket(
            ParticlePacket(
                config.landingParticle, landPos.x(), landPos.y(), landPos.z(),
                0.5f, 0.5f, 0.5f, 0.1f, 20
            )
        )

        instance.players.forEach { player ->
            player.playSound(config.landingSound, landPos)
        }

        config.onLand?.invoke(landPos)

        val loot = config.lootTable.roll()
        val inventory = Inventory(InventoryType.CHEST_3_ROW, mm("<gold>Supply Drop"))
        loot.forEachIndexed { index, item ->
            if (index < inventory.size) inventory.setItemStack(index, item)
        }

        val chestEntity = LivingEntity(EntityType.ARMOR_STAND)
        val chestMeta = chestEntity.entityMeta as ArmorStandMeta
        chestMeta.setNotifyAboutChanges(false)
        chestMeta.isInvisible = false
        chestMeta.isHasNoGravity = true
        chestMeta.isSmall = false
        chestMeta.setNotifyAboutChanges(true)
        chestEntity.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.CHEST))
        chestEntity.setInstance(instance, landPos).join()

        val interactNode = EventNode.all("supplydrop-interact-${chestEntity.entityId}")
        interactNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (event.target.entityId == chestEntity.entityId) {
                event.player.openInventory(inventory)
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(interactNode)

        delay(config.chestDurationTicks) {
            chestEntity.remove()
            MinecraftServer.getGlobalEventHandler().removeChild(interactNode)
        }
    }
}

class SupplyDropBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var origin: Pos = Pos(0.0, 200.0, 0.0)
    @PublishedApi internal var target: Pos = Pos(0.0, 64.0, 0.0)
    @PublishedApi internal var fallSpeed: Double = 0.5
    @PublishedApi internal var lootTable: LootTable? = null
    @PublishedApi internal var trailParticle: Particle = Particle.FLAME
    @PublishedApi internal var landingParticle: Particle = Particle.EXPLOSION
    @PublishedApi internal var landingSound: SoundEvent = SoundEvent.ENTITY_GENERIC_EXPLODE
    @PublishedApi internal var announceRadius: Double = 100.0
    @PublishedApi internal var announceKey: TranslationKey = "orbit.supplydrop.incoming".asTranslationKey()
    @PublishedApi internal var chestDurationTicks: Int = 600
    @PublishedApi internal var onLandHandler: ((Pos) -> Unit)? = null

    fun origin(pos: Pos) { origin = pos }
    fun target(pos: Pos) { target = pos }
    fun fallSpeed(speed: Double) { fallSpeed = speed }
    fun lootTable(table: LootTable) { lootTable = table }
    fun trailParticle(particle: Particle) { trailParticle = particle }
    fun landingParticle(particle: Particle) { landingParticle = particle }
    fun landingSound(sound: SoundEvent) { landingSound = sound }
    fun announceRadius(radius: Double) { announceRadius = radius }
    fun announceKey(key: String) { announceKey = key.asTranslationKey() }
    fun chestDuration(ticks: Int) { chestDurationTicks = ticks }
    fun onLand(handler: (Pos) -> Unit) { onLandHandler = handler }

    @PublishedApi internal fun build(): SupplyDrop {
        val table = requireNotNull(lootTable) { "SupplyDrop requires a lootTable" }
        return SupplyDrop(
            origin = origin,
            target = target,
            fallSpeed = fallSpeed,
            lootTable = table,
            trailParticle = trailParticle,
            landingParticle = landingParticle,
            landingSound = landingSound,
            announceRadius = announceRadius,
            announceKey = announceKey,
            chestDurationTicks = chestDurationTicks,
            onLand = onLandHandler,
        )
    }
}

inline fun supplyDrop(block: SupplyDropBuilder.() -> Unit): SupplyDrop =
    SupplyDropBuilder().apply(block).build()

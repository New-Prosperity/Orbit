package me.nebula.orbit.mechanic.piglinbarter

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val BARTERING_TAG = Tag.Boolean("mechanic:piglin_barter:bartering").defaultValue(false)
private val BARTER_START_TAG = Tag.Long("mechanic:piglin_barter:start").defaultValue(0L)

private const val BARTER_RANGE = 3.0
private const val BARTER_DURATION_MS = 6000L

private val BARTER_LOOT = listOf(
    Material.IRON_NUGGET to 10 to 9..36,
    Material.ENDER_PEARL to 10 to 2..6,
    Material.STRING to 10 to 3..9,
    Material.QUARTZ to 8 to 5..12,
    Material.OBSIDIAN to 8 to 1..1,
    Material.CRYING_OBSIDIAN to 8 to 1..3,
    Material.FIRE_CHARGE to 8 to 1..5,
    Material.LEATHER to 8 to 2..4,
    Material.SOUL_SAND to 5 to 2..8,
    Material.NETHER_BRICK to 5 to 2..8,
    Material.SPECTRAL_ARROW to 5 to 6..12,
    Material.GRAVEL to 5 to 8..16,
    Material.BLACKSTONE to 5 to 8..16,
    Material.GLOWSTONE_DUST to 3 to 2..4,
    Material.MAGMA_CREAM to 3 to 2..6,
    Material.NETHER_QUARTZ_ORE to 2 to 1..1,
    Material.IRON_BOOTS to 1 to 1..1,
    Material.SPLASH_POTION to 1 to 1..1,
    Material.POTION to 1 to 1..1,
    Material.ENCHANTED_BOOK to 1 to 1..1,
    Material.IRON_INGOT to 2 to 1..1,
)

class PiglinBarterModule : OrbitModule("piglin-barter") {

    private var tickTask: Task? = null
    private val activeBarterPiglins: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(10))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        activeBarterPiglins.clear()
        super.onDisable()
    }

    private fun tick() {
        activeBarterPiglins.removeIf { it.isRemoved }

        scanForGoldItems()
        processBartering()
    }

    private fun scanForGoldItems() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach

            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.PIGLIN) return@entityLoop
                if (entity.getTag(BARTERING_TAG)) return@entityLoop

                val goldItem = findNearbyGold(entity) ?: return@entityLoop

                goldItem.remove()
                entity.setTag(BARTERING_TAG, true)
                entity.setTag(BARTER_START_TAG, System.currentTimeMillis())
                activeBarterPiglins.add(entity)
            }
        }
    }

    private fun findNearbyGold(piglin: Entity): ItemEntity? {
        val instance = piglin.instance ?: return null
        return instance.getNearbyEntities(piglin.position, BARTER_RANGE)
            .filterIsInstance<ItemEntity>()
            .firstOrNull { it.itemStack.material() == Material.GOLD_INGOT }
    }

    private fun processBartering() {
        val now = System.currentTimeMillis()

        activeBarterPiglins.toList().forEach { piglin ->
            if (piglin.isRemoved) {
                activeBarterPiglins.remove(piglin)
                return@forEach
            }

            val start = piglin.getTag(BARTER_START_TAG)
            if (now - start < BARTER_DURATION_MS) return@forEach

            piglin.setTag(BARTERING_TAG, false)
            activeBarterPiglins.remove(piglin)
            dropBarterLoot(piglin)
        }
    }

    private fun dropBarterLoot(piglin: Entity) {
        val instance = piglin.instance ?: return
        val pos = piglin.position

        val (materialAndWeight, amountRange) = selectLoot()
        val (material, _) = materialAndWeight
        val amount = Random.nextInt(amountRange.first, amountRange.last + 1)

        val itemEntity = ItemEntity(ItemStack.of(material, amount))
        itemEntity.setPickupDelay(Duration.ofMillis(500))
        itemEntity.setInstance(instance, Pos(pos.x(), pos.y() + 0.5, pos.z()))

        itemEntity.scheduler().buildTask { itemEntity.remove() }
            .delay(TaskSchedule.minutes(5))
            .schedule()
    }

    private fun selectLoot(): Pair<Pair<Material, Int>, IntRange> {
        val totalWeight = BARTER_LOOT.sumOf { it.first.second }
        var roll = Random.nextInt(totalWeight)
        for (entry in BARTER_LOOT) {
            roll -= entry.first.second
            if (roll < 0) return entry
        }
        return BARTER_LOOT.first()
    }
}

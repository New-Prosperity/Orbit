package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.packBlockPos
import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val CAMPFIRE_NAMES = setOf("minecraft:campfire", "minecraft:soul_campfire")

private val CAMPFIRE_RECIPES = mapOf(
    Material.BEEF to Material.COOKED_BEEF,
    Material.PORKCHOP to Material.COOKED_PORKCHOP,
    Material.CHICKEN to Material.COOKED_CHICKEN,
    Material.MUTTON to Material.COOKED_MUTTON,
    Material.RABBIT to Material.COOKED_RABBIT,
    Material.COD to Material.COOKED_COD,
    Material.SALMON to Material.COOKED_SALMON,
    Material.POTATO to Material.BAKED_POTATO,
    Material.KELP to Material.DRIED_KELP,
)

private class CookingSlot(val input: Material, val output: Material, var ticksRemaining: Int)

private class CampfireBlockHandler(
    private val key: Key,
    private val campfires: ConcurrentHashMap<Long, MutableList<CookingSlot>>,
    private val cookTime: Int,
) : BlockHandler {

    override fun getKey(): Key = key

    override fun isTickable(): Boolean = true

    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        if (!VanillaModules.isEnabled(interaction.instance, "campfire-cooking")) return true

        val lit = interaction.block.getProperty("lit") ?: "true"
        if (lit == "false") return true

        val item = interaction.player.itemInMainHand
        val output = CAMPFIRE_RECIPES[item.material()] ?: return true

        val pos = interaction.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val slots = campfires.getOrPut(packed) { mutableListOf() }
        if (slots.size >= 4) return true

        slots.add(CookingSlot(item.material(), output, cookTime))
        if (interaction.player.gameMode != GameMode.CREATIVE) {
            interaction.player.setItemInMainHand(item.consume(1))
        }
        return false
    }

    override fun onDestroy(destroy: BlockHandler.Destroy) {
        if (!VanillaModules.isEnabled(destroy.instance, "campfire-cooking")) return
        val pos = destroy.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val slots = campfires.remove(packed) ?: return
        val x = pos.blockX()
        val y = pos.blockY()
        val z = pos.blockZ()
        for (slot in slots) {
            val itemEntity = ItemEntity(ItemStack.of(slot.input))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(destroy.instance, Pos(x + 0.5, y + 0.5, z + 0.5))
        }
    }

    override fun tick(tick: BlockHandler.Tick) {
        if (!VanillaModules.isEnabled(tick.instance, "campfire-cooking")) return
        val pos = tick.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val slots = campfires[packed] ?: return

        val finished = mutableListOf<Material>()
        var i = slots.size - 1
        while (i >= 0) {
            val slot = slots[i]
            val remaining = slot.ticksRemaining - 1
            if (remaining <= 0) {
                finished.add(slot.output)
                slots.removeAt(i)
            } else {
                slot.ticksRemaining = remaining
            }
            i--
        }
        if (slots.isEmpty()) campfires.remove(packed)

        val x = pos.blockX()
        val y = pos.blockY()
        val z = pos.blockZ()
        for (output in finished) {
            val itemEntity = ItemEntity(ItemStack.of(output))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(tick.instance, Pos(x + 0.5, y + 1.0, z + 0.5))
        }
    }
}

object CampfireCookingModule : VanillaModule {

    override val id = "campfire-cooking"
    override val description = "Place raw food on campfires to cook over time"
    override val configParams = listOf(
        ConfigParam.IntParam("cookTimeTicks", "Ticks to cook one item", 600, 100, 6000),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val cookTime = config.getInt("cookTimeTicks", 600)
        val campfires = ConcurrentHashMap<Long, MutableList<CookingSlot>>()

        val blockManager = MinecraftServer.getBlockManager()
        val campfireHandler = CampfireBlockHandler(Key.key("minecraft:campfire"), campfires, cookTime)
        val soulCampfireHandler = CampfireBlockHandler(Key.key("minecraft:soul_campfire"), campfires, cookTime)

        blockManager.registerHandler("minecraft:campfire") { campfireHandler }
        blockManager.registerHandler("minecraft:soul_campfire") { soulCampfireHandler }

        val node = EventNode.all("vanilla-campfire-cooking")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val blockName = event.block.name()
            if (blockName !in CAMPFIRE_NAMES) return@addListener
            val handler = if (blockName == "minecraft:soul_campfire") soulCampfireHandler else campfireHandler
            event.setBlock(event.block.withHandler(handler))
        }

        return node
    }
}

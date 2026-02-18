package me.nebula.orbit.utils.selectiontool

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.timer.TaskSchedule
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.region.CuboidRegion
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val miniMessage = MiniMessage.miniMessage()

data class Selection(val pos1: Point, val pos2: Point) {

    val min: Pos get() = Pos(
        minOf(pos1.x(), pos2.x()),
        minOf(pos1.y(), pos2.y()),
        minOf(pos1.z(), pos2.z()),
    )

    val max: Pos get() = Pos(
        maxOf(pos1.x(), pos2.x()),
        maxOf(pos1.y(), pos2.y()),
        maxOf(pos1.z(), pos2.z()),
    )

    val sizeX: Int get() = (max.x() - min.x() + 1).toInt()
    val sizeY: Int get() = (max.y() - min.y() + 1).toInt()
    val sizeZ: Int get() = (max.z() - min.z() + 1).toInt()
    val volume: Int get() = sizeX * sizeY * sizeZ

    fun toRegion(name: String): CuboidRegion = CuboidRegion(name, min, max)

    fun contains(point: Point): Boolean =
        point.x() in min.x()..max.x() &&
        point.y() in min.y()..max.y() &&
        point.z() in min.z()..max.z()
}

object SelectionManager {

    private val pos1Map = ConcurrentHashMap<UUID, Point>()
    private val pos2Map = ConcurrentHashMap<UUID, Point>()
    private val wandHolders = ConcurrentHashMap.newKeySet<UUID>()
    private var eventNode: EventNode<*>? = null
    private var particleTask: net.minestom.server.timer.Task? = null

    val wandItem = itemStack(Material.GOLDEN_AXE) {
        name("<gold>Selection Wand")
        lore("<gray>Left-click: Set pos1")
        lore("<gray>Right-click: Set pos2")
        glowing()
    }

    fun setPos1(uuid: UUID, point: Point) { pos1Map[uuid] = point }
    fun setPos2(uuid: UUID, point: Point) { pos2Map[uuid] = point }
    fun getPos1(uuid: UUID): Point? = pos1Map[uuid]
    fun getPos2(uuid: UUID): Point? = pos2Map[uuid]

    fun getSelection(uuid: UUID): Selection? {
        val p1 = pos1Map[uuid] ?: return null
        val p2 = pos2Map[uuid] ?: return null
        return Selection(p1, p2)
    }

    fun clearSelection(uuid: UUID) {
        pos1Map.remove(uuid)
        pos2Map.remove(uuid)
    }

    fun giveWand(player: Player) {
        player.inventory.addItemStack(wandItem)
        wandHolders.add(player.uuid)
        if (eventNode == null) install()
    }

    fun removeWand(player: Player) {
        wandHolders.remove(player.uuid)
        for (i in 0 until player.inventory.size) {
            if (player.inventory.getItemStack(i) == wandItem) {
                player.inventory.setItemStack(i, net.minestom.server.item.ItemStack.AIR)
            }
        }
        clearSelection(player.uuid)
    }

    fun install() {
        if (eventNode != null) return
        val node = EventNode.all("selection-tool")

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (!wandHolders.contains(event.player.uuid)) return@addListener
            if (event.player.inventory.getItemStack(event.player.heldSlot.toInt()) != wandItem) return@addListener
            event.isCancelled = true
            val point = event.blockPosition
            setPos1(event.player.uuid, point)
            event.player.sendMessage(miniMessage.deserialize("<green>Pos1 set to <yellow>${point.x()}, ${point.y()}, ${point.z()}"))
        }

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (!wandHolders.contains(event.player.uuid)) return@addListener
            if (event.player.inventory.getItemStack(event.player.heldSlot.toInt()) != wandItem) return@addListener
            val point = event.blockPosition
            setPos2(event.player.uuid, point)
            event.player.sendMessage(miniMessage.deserialize("<green>Pos2 set to <yellow>${point.x()}, ${point.y()}, ${point.z()}"))
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        particleTask = MinecraftServer.getSchedulerManager().buildTask {
            wandHolders.forEach { uuid ->
                val player = MinecraftServer.getConnectionManager().onlinePlayers
                    .firstOrNull { it.uuid == uuid } ?: return@forEach
                val selection = getSelection(uuid) ?: return@forEach
                showSelectionParticles(player, selection)
            }
        }.repeat(TaskSchedule.tick(10)).schedule()
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        particleTask?.cancel()
        particleTask = null
        wandHolders.clear()
        pos1Map.clear()
        pos2Map.clear()
    }

    private fun showSelectionParticles(player: Player, selection: Selection) {
        val min = selection.min
        val max = selection.max.add(1.0, 1.0, 1.0)
        val step = 0.5

        drawEdge(player, min.x(), min.y(), min.z(), max.x(), min.y(), min.z(), step)
        drawEdge(player, min.x(), min.y(), min.z(), min.x(), max.y(), min.z(), step)
        drawEdge(player, min.x(), min.y(), min.z(), min.x(), min.y(), max.z(), step)
        drawEdge(player, max.x(), max.y(), max.z(), min.x(), max.y(), max.z(), step)
        drawEdge(player, max.x(), max.y(), max.z(), max.x(), min.y(), max.z(), step)
        drawEdge(player, max.x(), max.y(), max.z(), max.x(), max.y(), min.z(), step)
        drawEdge(player, min.x(), max.y(), min.z(), max.x(), max.y(), min.z(), step)
        drawEdge(player, min.x(), max.y(), min.z(), min.x(), max.y(), max.z(), step)
        drawEdge(player, max.x(), min.y(), min.z(), max.x(), max.y(), min.z(), step)
        drawEdge(player, max.x(), min.y(), min.z(), max.x(), min.y(), max.z(), step)
        drawEdge(player, min.x(), min.y(), max.z(), max.x(), min.y(), max.z(), step)
        drawEdge(player, min.x(), min.y(), max.z(), min.x(), max.y(), max.z(), step)
    }

    private fun drawEdge(player: Player, x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, step: Double) {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        val steps = (dist / step).toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val packet = ParticlePacket(
                Particle.DUST.withColor(TextColor.color(51, 204, 255)),
                x1 + dx * t, y1 + dy * t, z1 + dz * t,
                0f, 0f, 0f, 0f, 1,
            )
            player.sendPacket(packet)
        }
    }
}

fun Player.setPos1(point: Point) = SelectionManager.setPos1(uuid, point)
fun Player.setPos2(point: Point) = SelectionManager.setPos2(uuid, point)
fun Player.getSelection(): Selection? = SelectionManager.getSelection(uuid)

fun Player.fillSelection(block: Block) {
    val selection = getSelection() ?: return
    val instance = instance ?: return
    val min = selection.min
    val max = selection.max
    for (x in min.blockX()..max.blockX()) {
        for (y in min.blockY()..max.blockY()) {
            for (z in min.blockZ()..max.blockZ()) {
                instance.setBlock(x, y, z, block)
            }
        }
    }
}

fun Player.clearSelection() {
    fillSelection(Block.AIR)
}

fun Player.countBlocks(): Map<Block, Int> {
    val selection = getSelection() ?: return emptyMap()
    val instance = instance ?: return emptyMap()
    val counts = mutableMapOf<Block, Int>()
    val min = selection.min
    val max = selection.max
    for (x in min.blockX()..max.blockX()) {
        for (y in min.blockY()..max.blockY()) {
            for (z in min.blockZ()..max.blockZ()) {
                val block = instance.getBlock(x, y, z)
                if (block != Block.AIR) {
                    counts[block] = (counts[block] ?: 0) + 1
                }
            }
        }
    }
    return counts.toMap()
}

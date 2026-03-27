package me.nebula.orbit.cosmetic

import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.cooldown.Cooldown
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.particle.ParticleShape
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.particle.Particle
import net.minestom.server.tag.Tag
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val GADGET_TAG = Tag.String("cosmetic:gadget")

object GadgetManager {

    private val cooldowns = ConcurrentHashMap<String, Cooldown<UUID>>()
    private val activeGadgets = ConcurrentHashMap<UUID, String>()
    private var eventNode: EventNode<*>? = null

    const val GADGET_SLOT = 4

    fun install() {
        val node = EventNode.all("gadget-manager")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            unequip(event.player)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        activeGadgets.clear()
        cooldowns.clear()
    }

    fun equip(player: Player, cosmeticId: String, level: Int) {
        unequip(player)
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val material = Material.fromKey(definition.material) ?: Material.STICK
        val item = itemStack(material) {
            name("<green>${player.translateRaw(definition.nameKey)}")
        }.withTag(GADGET_TAG, cosmeticId)
        player.inventory.setItemStack(GADGET_SLOT, item)
        activeGadgets[player.uuid] = cosmeticId
    }

    fun unequip(player: Player) {
        activeGadgets.remove(player.uuid) ?: return
        player.inventory.setItemStack(GADGET_SLOT, ItemStack.AIR)
    }

    fun isActive(playerId: UUID): Boolean = activeGadgets.containsKey(playerId)

    fun onUse(player: Player) {
        val cosmeticId = activeGadgets[player.uuid] ?: return
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val data = CosmeticDataCache.get(player.uuid) ?: return
        val level = data.owned[cosmeticId] ?: return
        val resolved = definition.resolveData(level)

        val cooldownSec = resolved["cooldownSeconds"]?.toLongOrNull() ?: 5L
        val cooldown = cooldowns.getOrPut(cosmeticId) { Cooldown(Duration.ofSeconds(cooldownSec)) }
        if (!cooldown.tryUse(player.uuid)) return

        val action = resolved["action"] ?: return
        executeAction(player, action, resolved)
    }

    private fun executeAction(player: Player, action: String, resolved: Map<String, String>) {
        val instance = player.instance ?: return
        when (action) {
            "firework_launcher" -> {
                val force = resolved["force"]?.toDoubleOrNull() ?: 25.0
                player.velocity = Vec(0.0, force, 0.0)
                val particle = resolveParticle(resolved["particle"]) ?: Particle.FIREWORK
                CosmeticApplier.spawnGadgetParticle(instance, player.position, particle, player.uuid)
            }
            "paint_blaster" -> {
                val particle = resolveParticle(resolved["particle"]) ?: Particle.ITEM_SNOWBALL
                val radius = resolved["radius"]?.toDoubleOrNull() ?: 3.0
                val shape = ParticleShape.Sphere(player.position.add(0.0, 1.0, 0.0), radius, 20, particle)
                CosmeticApplier.spawnGadgetShape(instance, player.uuid, shape)
            }
            "grappling_hook" -> {
                val speed = resolved["speed"]?.toDoubleOrNull() ?: 2.0
                val yaw = Math.toRadians(-player.position.yaw().toDouble())
                val pitch = Math.toRadians(-player.position.pitch().toDouble())
                val dx = -kotlin.math.sin(yaw) * kotlin.math.cos(pitch) * speed
                val dy = kotlin.math.sin(pitch) * speed
                val dz = kotlin.math.cos(yaw) * kotlin.math.cos(pitch) * speed
                player.velocity = Vec(dx * 20.0, dy * 20.0, dz * 20.0)
            }
        }
    }

    private fun resolveParticle(name: String?): Particle? {
        if (name == null) return null
        return runCatching { Particle.fromKey("minecraft:${name.lowercase()}") }.getOrNull()
    }
}
